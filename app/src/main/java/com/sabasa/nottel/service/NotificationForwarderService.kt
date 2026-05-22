package com.sabasa.nottel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.sabasa.nottel.data.db.DatabaseRepository
import com.sabasa.nottel.data.db.NotificationEntity
import com.sabasa.nottel.data.prefs.PrefsManager
import com.sabasa.nottel.data.prefs.SettingsRepository
import com.sabasa.nottel.telegram.TelegramSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationForwarderService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var db: DatabaseRepository
    private lateinit var settings: SettingsRepository
    private lateinit var prefs: PrefsManager
    private lateinit var sender: TelegramSender
    private lateinit var batchQueue: NotificationBatchQueue

    // In-memory duplicate tracker: packageName → last "title|message"
    private val lastContentMap = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        db = DatabaseRepository(this)
        settings = SettingsRepository(this)
        prefs = PrefsManager(this)
        sender = TelegramSender()
        batchQueue = NotificationBatchQueue(
            scope = serviceScope,
            settings = settings,
            onFlush = { items -> handleFlush(items) }
        )
        startForegroundWithPlaceholder()
    }

    override fun onDestroy() {
        // Drain any remaining queued items before the service dies
        serviceScope.launch {
            batchQueue.flush()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        ForwardingState.isActive.value = true
    }

    override fun onListenerDisconnected() {
        // Listener was revoked by the OS or user — reflect in state
        ForwardingState.isActive.value = false
        requestRebind(componentName)  // ask the OS to reconnect
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Skip ongoing (non-clearable) notifications — these are typically
        // system UI, media players, and other service notifications
        if (!sbn.isClearable) return

        val appPackage = sbn.packageName
        val appLabel = resolveAppLabel(appPackage)
        val extras = sbn.notification.extras

        // Safe extraction — some OEMs return plain String instead of SpannableString
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val message = (
                extras.getCharSequence("android.bigText")
                    ?: extras.getCharSequence("android.text")
                    ?: sbn.notification.tickerText
                    ?: ""
                ).toString()

        // Nothing to forward — skip silently, don't pollute the history
        if (title.isBlank() && message.isBlank()) return

        val timestamp = System.currentTimeMillis()
        val contentKey = "$title|$message"

        // --- Filter chain (order matters) ---
        val skipReason: String? = when {
            !ForwardingState.isActive.value  -> "paused"
            settings.isQuietHoursActive()    -> "quiet_hours"
            !settings.isAppAllowed(appPackage) -> "filtered"
            lastContentMap[appPackage] == contentKey -> "duplicate"
            else -> null
        }

        val entity = NotificationEntity(
            appPackage = appPackage,
            appLabel = appLabel,
            title = title,
            message = message,
            timestamp = timestamp,
            wasForwarded = false,
            forwardError = skipReason  // null if passed all filters
        )

        serviceScope.launch {
            if (skipReason != null) {
                db.insert(entity)
                return@launch
            }
            // Passed all filters — track content, persist, enqueue
            lastContentMap[appPackage] = contentKey
            val id = db.insert(entity)
            batchQueue.enqueue(entity.copy(id = id))
        }
    }

    // --- Flush handler — called by NotificationBatchQueue ---

    private suspend fun handleFlush(items: List<NotificationEntity>) {
        if (!prefs.hasCredentials()) {
            // No credentials configured yet — mark all as error
            items.forEach { db.updateForwardStatus(it.id, false, "no_credentials") }
            return
        }

        val result = sender.sendBatch(
            token = prefs.botToken,
            chatId = prefs.chatId,
            items = items,
            includeAppName = settings.includeAppName
        )

        val wasForwarded = result.isSuccess
        val error = if (result.isFailure) {
            result.exceptionOrNull()?.message ?: "unknown_error"
        } else null

        items.forEach { db.updateForwardStatus(it.id, wasForwarded, error) }
    }

    // --- Helpers ---

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            // Package uninstalled mid-notification or restricted — fall back to package name
            packageName
        }
    }

    // Placeholder foreground notification — will be replaced in T07
    private fun startForegroundWithPlaceholder() {
        val channelId = CHANNEL_ID
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) == null) {
            NotificationChannel(channelId, "Forwarder Service", NotificationManager.IMPORTANCE_LOW)
                .also { manager.createNotificationChannel(it) }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Notification Forwarder")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "nottel_service"
    }
}