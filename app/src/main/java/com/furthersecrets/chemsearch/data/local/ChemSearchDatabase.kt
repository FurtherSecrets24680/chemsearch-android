package com.furthersecrets.chemsearch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadedCompoundEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChemSearchDatabase : RoomDatabase() {
    abstract fun downloadedCompoundDao(): DownloadedCompoundDao

    companion object {
        @Volatile
        private var instance: ChemSearchDatabase? = null

        fun getInstance(context: Context): ChemSearchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChemSearchDatabase::class.java,
                    "chemsearch.db"
                ).build().also { instance = it }
            }
    }
}
