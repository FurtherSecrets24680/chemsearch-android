package com.furthersecrets.chemsearch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedCompoundDao {
    @Query("SELECT * FROM downloaded_compounds ORDER BY savedAt DESC")
    fun observeDownloads(): Flow<List<DownloadedCompoundEntity>>

    @Query("SELECT * FROM downloaded_compounds ORDER BY savedAt DESC")
    suspend fun getDownloads(): List<DownloadedCompoundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadedCompoundEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(downloads: List<DownloadedCompoundEntity>)

    @Query("DELETE FROM downloaded_compounds WHERE cid = :cid")
    suspend fun delete(cid: Long)
}
