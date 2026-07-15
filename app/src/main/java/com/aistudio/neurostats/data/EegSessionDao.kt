package com.aistudio.neurostats.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EegSessionDao {
    @Query("SELECT * FROM eeg_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<EegSession>>

    @Query("SELECT * FROM eeg_sessions WHERE id = :id LIMIT 1")
    fun getSessionById(id: Int): Flow<EegSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: EegSession): Long

    @Delete
    suspend fun delete(session: EegSession)
}
