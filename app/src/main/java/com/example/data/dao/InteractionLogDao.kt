package com.example.data.dao

import androidx.room.*
import com.example.data.model.InteractionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: InteractionLogEntity): Long

    @Query("SELECT * FROM interaction_logs WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getLogsForContactFlow(contactId: Long): Flow<List<InteractionLogEntity>>

    @Query("SELECT * FROM interaction_logs WHERE contactId = :contactId ORDER BY timestamp DESC")
    suspend fun getLogsForContact(contactId: Long): List<InteractionLogEntity>

    @Query("SELECT * FROM interaction_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<InteractionLogEntity>>

    @Query("SELECT * FROM interaction_logs")
    suspend fun getAllLogs(): List<InteractionLogEntity>
}
