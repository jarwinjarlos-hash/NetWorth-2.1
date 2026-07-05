package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AssetCategory
import com.example.data.model.Asset
import com.example.data.model.Transaction
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.Bucket

@Database(entities = [AssetCategory::class, Asset::class, Transaction::class, PortfolioSnapshot::class, Bucket::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financialDao(): FinancialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "networth_tracker_v2"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
