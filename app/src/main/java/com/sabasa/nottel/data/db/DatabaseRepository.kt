package com.sabasa.nottel.data.db

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DatabaseRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).notificationDao()

    // 30 days in milliseconds
    private val retentionMs = 30L * 24 * 60 * 60 * 1000

    suspend fun insert(entity: NotificationEntity): Long {
        val id = dao.insert(entity)
        // Fire-and-forget retention cleanup — does not block the insert caller
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteOlderThan(System.currentTimeMillis() - retentionMs)
        }
        return id
    }

    suspend fun updateForwardStatus(id: Long, wasForwarded: Boolean, forwardError: String? = null) {
        dao.updateForwardStatus(id, wasForwarded, forwardError)
    }

    fun getAll(): Flow<List<NotificationEntity>> = dao.getAll()

    suspend fun deleteAll() = dao.deleteAll()
}