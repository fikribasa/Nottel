package com.sabasa.nottel.service

import com.sabasa.nottel.data.db.NotificationEntity
import com.sabasa.nottel.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationBatchQueue(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val onFlush: suspend (List<NotificationEntity>) -> Unit
) {
    private val queue = mutableListOf<NotificationEntity>()
    private val mutex = Mutex()
    private var flushJob: Job? = null

    fun enqueue(entity: NotificationEntity) {
        scope.launch {
            val shouldFlushNow = mutex.withLock {
                queue.add(entity)

                when {
                    // First item — start the max-wait timer
                    queue.size == 1 -> {
                        flushJob = scope.launch {
                            delay(settings.batchMaxWaitMs)
                            dispatchFlush()
                        }
                        false // timer will handle it
                    }
                    // Count threshold reached — signal immediate flush
                    queue.size >= settings.batchMaxCount -> {
                        flushJob?.cancel()
                        flushJob = null
                        true
                    }
                    else -> false
                }
            }

            if (shouldFlushNow) dispatchFlush()
        }
    }

    // Public — called by the service in onDestroy to drain remaining items
    suspend fun flush() = dispatchFlush()

    // Snapshot under lock, then send outside lock so enqueue() isn't blocked
    // during the HTTP call
    private suspend fun dispatchFlush() {
        val snapshot = mutex.withLock {
            if (queue.isEmpty()) return
            val items = queue.toList()
            queue.clear()
            flushJob?.cancel()
            flushJob = null
            items
        }
        // onFlush (HTTP call) runs here — outside the mutex
        onFlush(snapshot)
    }
}