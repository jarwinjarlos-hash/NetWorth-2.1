package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.repository.FinancialRepository

class GoogleDriveSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FinancialRepository(database.financialDao())
        val syncManager = GoogleDriveSyncManager(applicationContext, repository)

        if (!syncManager.isUserSignedIn()) {
            return Result.failure()
        }

        return try {
            val result = syncManager.uploadLocalToCloud()
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
