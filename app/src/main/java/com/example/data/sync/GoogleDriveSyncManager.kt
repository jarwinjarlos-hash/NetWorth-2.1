package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.repository.FinancialRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DriveSyncPayload(
    val categories: List<AssetCategory> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val buckets: List<Bucket> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val snapshots: List<PortfolioSnapshot> = emptyList(),
    val lastUpdated: Long = 0L,
    val settingsJson: String? = null,
    val customLogos: Map<String, String>? = null
)

class GoogleDriveApiException(val code: Int, val errorBody: String) : java.io.IOException("Google Drive API HTTP $code: $errorBody")

class GoogleDriveSyncManager(
    private val context: Context,
    private val repository: FinancialRepository
) {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val payloadAdapter = moshi.adapter(DriveSyncPayload::class.java)

    companion object {
        const val BACKUP_FILE_NAME = "networth_backup.json"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }

    // Check if user is signed in with appropriate Drive scope
    fun isUserSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(SCOPE_DRIVE_APPDATA))
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(SCOPE_DRIVE_APPDATA))) {
            account
        } else {
            null
        }
    }

    fun getSignInIntent() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE_APPDATA))
            .build()
    ).signInIntent

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: return@withContext null
        try {
            // Retrieve actual OAuth token using GoogleAuthUtil
            GoogleAuthUtil.getToken(
                context,
                account.account ?: android.accounts.Account(account.email ?: "", "com.google"),
                "oauth2:$SCOPE_DRIVE_APPDATA"
            )
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Failed to retrieve access token", e)
            null
        }
    }

    // Find remote backup file ID and its modification timestamp
    private suspend fun findRemoteBackupFile(token: String): RemoteFileMetadata? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BACKUP_FILE_NAME'&fields=files(id,name,modifiedTime)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GoogleDriveSyncManager", "Search file failed: ${response.code} ${response.message} - $errorBody")
                    throw GoogleDriveApiException(response.code, errorBody)
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    val id = fileObj.getString("id")
                    val modifiedTimeStr = fileObj.getString("modifiedTime")
                    val modifiedTime = parseIsoTimestamp(modifiedTimeStr)
                    return@withContext RemoteFileMetadata(id, modifiedTime)
                }
            }
        } catch (e: GoogleDriveApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Exception during finding remote file", e)
            throw e
        }
        null
    }

    // Helper to parse ISO format timestamps from Drive API metadata
    private fun parseIsoTimestamp(isoStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(isoStr)?.time ?: System.currentTimeMillis()
            } catch (ex: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    // Create a new backup file in Google Drive AppData folder using multipart upload
    private suspend fun createRemoteBackupFile(token: String, payloadJson: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val boundary = "DriveSyncBoundary"
        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append("{\r\n")
            append("  \"name\": \"$BACKUP_FILE_NAME\",\r\n")
            append("  \"parents\": [\"appDataFolder\"]\r\n")
            append("}\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(payloadJson)
            append("\r\n--$boundary--")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "multipart/related; boundary=$boundary")
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveSyncManager", "Created backup file successfully")
                    true
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GoogleDriveSyncManager", "Create file failed: ${response.code} ${response.message} - $errorBody")
                    throw GoogleDriveApiException(response.code, errorBody)
                }
            }
        } catch (e: GoogleDriveApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Exception during creating remote file", e)
            throw e
        }
    }

    // Update existing backup file in Google Drive AppData folder
    private suspend fun updateRemoteBackupFile(token: String, fileId: String, payloadJson: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .patch(payloadJson.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveSyncManager", "Updated backup file successfully")
                    true
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GoogleDriveSyncManager", "Update file failed: ${response.code} ${response.message} - $errorBody")
                    throw GoogleDriveApiException(response.code, errorBody)
                }
            }
        } catch (e: GoogleDriveApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Exception during updating remote file", e)
            throw e
        }
    }

    // Download content of a backup file from Google Drive
    private suspend fun downloadRemoteBackupFile(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GoogleDriveSyncManager", "Download file failed: ${response.code} ${response.message} - $errorBody")
                    throw GoogleDriveApiException(response.code, errorBody)
                }
            }
        } catch (e: GoogleDriveApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Exception during downloading remote file", e)
            throw e
        }
    }

    // Core Sync Operations

    private suspend fun downloadCloudToLocalInternal(token: String, force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val remoteFile = findRemoteBackupFile(token) ?: return@withContext Result.failure(Exception("No backup file found in Google Drive appDataFolder"))
        val payloadJson = downloadRemoteBackupFile(token, remoteFile.id) ?: return@withContext Result.failure(Exception("Failed to download cloud backup content"))
        
        val payload = payloadAdapter.fromJson(payloadJson) ?: return@withContext Result.failure(Exception("Failed to parse cloud backup payload"))

        if (!force) {
            // Perform Last-Write-Wins: if local data has a more recent update, reject replacement or raise conflict
            val localLastUpdated = getLocalMaxLastUpdated()
            if (localLastUpdated == payload.lastUpdated) {
                Log.d("GoogleDriveSyncManager", "Local database is already perfectly up to date with Google Drive cloud backup. Skipping DB reload.")
                return@withContext Result.success(Unit)
            }
            if (localLastUpdated > payload.lastUpdated) {
                return@withContext Result.failure(Exception("Local database is newer than Google Drive cloud backup"))
            }
        }

        // Clear old database contents completely to match the cloud backup exactly (deleting anything that was deleted in the cloud)
        repository.clearTransactions()
        repository.clearAssets()
        repository.clearCategories()
        repository.clearBuckets()
        repository.clearSnapshots()

        // Restore elements safely inside the database
        payload.categories.forEach { repository.insertCategory(it.copy(isDirty = false)) }
        payload.buckets.forEach { repository.insertBucket(it.copy(isDirty = false)) }
        payload.assets.forEach { repository.insertAsset(it.copy(isDirty = false)) }
        payload.transactions.forEach { repository.insertTransaction(it.copy(isDirty = false)) }
        payload.snapshots.forEach { repository.insertSnapshot(it.copy(isDirty = false)) }

        // Restore custom logos if any
        payload.customLogos?.forEach { (assetIdStr, b64) ->
            if (b64.isNotEmpty()) {
                try {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    val file = java.io.File(context.filesDir, "wallet_icon_${assetIdStr}.jpg")
                    file.writeBytes(bytes)
                    Log.d("GoogleDriveSyncManager", "Restored custom logo file for assetId $assetIdStr")
                } catch (e: Exception) {
                    Log.e("GoogleDriveSyncManager", "Failed to restore custom logo file for assetId $assetIdStr", e)
                }
            }
        }

        // Restore type-preserved settings/preferences
        restoreSettingsFromJson(payload.settingsJson)

        clearAllDeletions()

        Result.success(Unit)
    }

    // Pull cloud backup, compare and deserialize to local DB
    suspend fun downloadCloudToLocal(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext Result.failure(Exception("Google Account Sign-In required or Drive access not authorized"))
        
        try {
            downloadCloudToLocalInternal(token, force)
        } catch (e: GoogleDriveApiException) {
            if (e.code == 401 || e.code == 403) {
                Log.w("GoogleDriveSyncManager", "Auth error (${e.code}) on download, clearing token and retrying...", e)
                try {
                    GoogleAuthUtil.clearToken(context, token)
                } catch (clearEx: Exception) {
                    Log.e("GoogleDriveSyncManager", "Failed to clear token", clearEx)
                }
                val newToken = getAccessToken()
                if (newToken != null && newToken != token) {
                    try {
                        downloadCloudToLocalInternal(newToken, force)
                    } catch (retryEx: Exception) {
                        Result.failure(retryEx)
                    }
                } else {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Sync download failed", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadLocalToCloudInternal(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val categories = repository.allCategories.first()
        val assets = repository.allAssets.first()
        val buckets = repository.allBuckets.first()
        val transactions = repository.allTransactions.first()
        val snapshots = repository.allSnapshots.first()

        val customLogosMap = mutableMapOf<String, String>()
        val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("wallet_icon_") && value is String && value.startsWith("file://")) {
                val assetId = key.substringAfter("wallet_icon_")
                val file = java.io.File(context.filesDir, "wallet_icon_${assetId}.jpg")
                if (file.exists()) {
                    try {
                        val bytes = file.readBytes()
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        customLogosMap[assetId] = b64
                    } catch (e: Exception) {
                        Log.e("GoogleDriveSyncManager", "Failed to read custom logo file for assetId $assetId", e)
                    }
                }
            }
        }

        val maxLocalTimestamp = getLocalMaxLastUpdated()
        val payload = DriveSyncPayload(
            categories = categories,
            assets = assets,
            buckets = buckets,
            transactions = transactions,
            snapshots = snapshots,
            lastUpdated = maxLocalTimestamp,
            settingsJson = getSettingsJson(),
            customLogos = customLogosMap
        )

        val payloadJson = payloadAdapter.toJson(payload)
        val remoteFile = findRemoteBackupFile(token)

        val success = if (remoteFile != null) {
            // Last-Write-Wins check
            if (remoteFile.modifiedTime > maxLocalTimestamp) {
                return@withContext Result.failure(Exception("Cloud backup is newer than local database. Please download first to merge or resolve."))
            }
            updateRemoteBackupFile(token, remoteFile.id, payloadJson)
        } else {
            createRemoteBackupFile(token, payloadJson)
        }

        if (success) {
            // Clear dirty flags from local database
            clearAllDirtyFlags()
            clearAllDeletions()
            Result.success(Unit)
        } else {
            Result.failure(Exception("Google Drive File Transport operation failed"))
        }
    }

    // Push local database to Google Drive, resolving conflict using Last-Write-Wins
    suspend fun uploadLocalToCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext Result.failure(Exception("Google Account Sign-In required or Drive access not authorized"))

        try {
            uploadLocalToCloudInternal(token)
        } catch (e: GoogleDriveApiException) {
            if (e.code == 401 || e.code == 403) {
                Log.w("GoogleDriveSyncManager", "Auth error (${e.code}) on upload, clearing token and retrying...", e)
                try {
                    GoogleAuthUtil.clearToken(context, token)
                } catch (clearEx: Exception) {
                    Log.e("GoogleDriveSyncManager", "Failed to clear token", clearEx)
                }
                val newToken = getAccessToken()
                if (newToken != null && newToken != token) {
                    try {
                        uploadLocalToCloudInternal(newToken)
                    } catch (retryEx: Exception) {
                        Result.failure(retryEx)
                    }
                } else {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Sync upload failed", e)
            Result.failure(e)
        }
    }

    private val deletionPrefs = context.getSharedPreferences("networth_sync_deletions", Context.MODE_PRIVATE)

    fun recordDeletion(type: String, id: Int) {
        val key = "$type:$id"
        val now = System.currentTimeMillis()
        deletionPrefs.edit().putLong(key, now).apply()
        Log.d("GoogleDriveSyncManager", "Recorded deletion: $key at $now")
    }

    fun getDeletions(): Map<String, Long> {
        val all = deletionPrefs.all
        val map = mutableMapOf<String, Long>()
        for ((k, v) in all) {
            val value = v
            if (value is Long) {
                map[k] = value
            }
        }
        return map
    }

    fun clearAllDeletions() {
        deletionPrefs.edit().clear().apply()
        Log.d("GoogleDriveSyncManager", "Cleared all recorded deletions")
    }

    suspend fun hasDirtyData(): Boolean {
        val cats = repository.allCategories.first().any { it.isDirty }
        val assets = repository.allAssets.first().any { it.isDirty }
        val buckets = repository.allBuckets.first().any { it.isDirty }
        val txs = repository.allTransactions.first().any { it.isDirty }
        val snaps = repository.allSnapshots.first().any { it.isDirty }
        val hasDeletions = getDeletions().isNotEmpty()
        val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
        val prefsIsDirty = prefs.getBoolean("prefs_is_dirty", false)
        return cats || assets || buckets || txs || snaps || hasDeletions || prefsIsDirty
    }

    // Helper to clear dirty flags upon successful sync
    private suspend fun clearAllDirtyFlags() {
        val categories = repository.allCategories.first().filter { it.isDirty }
        val assets = repository.allAssets.first().filter { it.isDirty }
        val buckets = repository.allBuckets.first().filter { it.isDirty }
        val transactions = repository.allTransactions.first().filter { it.isDirty }
        val snapshots = repository.allSnapshots.first().filter { it.isDirty }

        categories.forEach { repository.updateCategory(it.copy(isDirty = false)) }
        assets.forEach { repository.updateAsset(it.copy(isDirty = false)) }
        buckets.forEach { repository.updateBucket(it.copy(isDirty = false)) }
        transactions.forEach { repository.updateTransaction(it.copy(isDirty = false)) }
        snapshots.forEach { repository.insertSnapshot(it.copy(isDirty = false)) }

        val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("prefs_is_dirty", false).apply()
    }

    // Helper to calculate maximum lastUpdated timestamp across all local data
    private suspend fun getLocalMaxLastUpdated(): Long {
        var maxTime = 0L
        val cats = repository.allCategories.first()
        val assets = repository.allAssets.first()
        val buckets = repository.allBuckets.first()
        val txs = repository.allTransactions.first()
        val snaps = repository.allSnapshots.first()

        cats.forEach { if (it.lastUpdated > maxTime) maxTime = it.lastUpdated }
        assets.forEach { if (it.lastUpdated > maxTime) maxTime = it.lastUpdated }
        buckets.forEach { if (it.lastUpdated > maxTime) maxTime = it.lastUpdated }
        txs.forEach { if (it.lastUpdated > maxTime) maxTime = it.lastUpdated }
        snaps.forEach { if (it.lastUpdated > maxTime) maxTime = it.lastUpdated }

        getDeletions().values.forEach { if (it > maxTime) maxTime = it }

        val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
        val prefsLastUpdated = prefs.getLong("prefs_last_updated", 0L)
        if (prefsLastUpdated > maxTime) {
            maxTime = prefsLastUpdated
        }

        if (assets.isEmpty() && buckets.isEmpty() && txs.isEmpty() && snaps.isEmpty() && getDeletions().isEmpty() && prefsLastUpdated == 0L) {
            return 0L
        }

        return if (maxTime == 0L) System.currentTimeMillis() else maxTime
    }

    private fun getSettingsJson(): String {
        return try {
            val root = JSONObject()
            val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
            val allEntries = prefs.all
            
            allEntries.forEach { (key, value) ->
                if (key == "auto_sync_drive") return@forEach
                if (value == null) return@forEach
                
                val item = JSONObject()
                when (value) {
                    is Boolean -> {
                        item.put("type", "boolean")
                        item.put("value", value)
                    }
                    is Int -> {
                        item.put("type", "int")
                        item.put("value", value)
                    }
                    is Long -> {
                        item.put("type", "long")
                        item.put("value", value)
                    }
                    is Float -> {
                        item.put("type", "float")
                        item.put("value", value.toDouble())
                    }
                    is Double -> {
                        item.put("type", "double")
                        item.put("value", value)
                    }
                    is String -> {
                        item.put("type", "string")
                        item.put("value", value)
                    }
                    else -> return@forEach
                }
                root.put(key, item)
            }
            root.toString()
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Error serializing preferences", e)
            "{}"
        }
    }

    private fun restoreSettingsFromJson(settingsJson: String?) {
        if (settingsJson.isNullOrBlank()) return
        try {
            val root = JSONObject(settingsJson)
            val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Capture existing auto-sync setting and clear everything else
            // so any deleted settings (like budgets) are deleted locally too
            val autoSyncEnabled = prefs.getBoolean("auto_sync_drive", false)
            editor.clear()
            editor.putBoolean("auto_sync_drive", autoSyncEnabled)
            
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "auto_sync_drive") continue
                
                val item = root.optJSONObject(key) ?: continue
                val type = item.optString("type")
                if (item.isNull("value")) {
                    editor.remove(key)
                    continue
                }
                
                when (type) {
                    "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                    "int" -> editor.putInt(key, item.getInt("value"))
                    "long" -> editor.putLong(key, item.getLong("value"))
                    "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                    "double" -> editor.putFloat(key, item.getDouble("value").toFloat())
                    "string" -> {
                        var stringValue = item.getString("value")
                        if (key.startsWith("wallet_icon_") && stringValue.startsWith("file://")) {
                            val assetIdStr = key.substringAfter("wallet_icon_")
                            val localFile = java.io.File(context.filesDir, "wallet_icon_${assetIdStr}.jpg")
                            stringValue = "file://" + localFile.absolutePath
                        }
                        editor.putString(key, stringValue)
                    }
                }
            }
            editor.apply()
            Log.d("GoogleDriveSyncManager", "Successfully restored type-preserved settings from cloud backup")
        } catch (e: Exception) {
            Log.e("GoogleDriveSyncManager", "Error restoring preferences from JSON", e)
        }
    }

    private data class RemoteFileMetadata(val id: String, val modifiedTime: Long)
}
