package com.sabasa.nottel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(entity: NotificationEntity): Long

    // Returns the full list as a live stream — the History screen observes this
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    // Called after a batch send completes — updates the pending records to forwarded
    @Query("""
        UPDATE notifications 
        SET wasForwarded = :wasForwarded, forwardError = :forwardError 
        WHERE id = :id
    """)
    suspend fun updateForwardStatus(id: Long, wasForwarded: Boolean, forwardError: String?)

    // Retention cleanup — called on every insert, deletes records older than 30 days
    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}