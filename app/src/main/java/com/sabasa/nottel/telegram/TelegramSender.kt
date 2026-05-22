package com.sabasa.nottel.telegram

import com.sabasa.nottel.data.db.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TelegramSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // --- Public API ---

    suspend fun sendBatch(
        token: String,
        chatId: String,
        items: List<NotificationEntity>,
        includeAppName: Boolean = true
    ): Result<Unit> {
        if (items.isEmpty()) return Result.success(Unit)
        val text = formatBatch(items, includeAppName)
        return post(token, chatId, text)
    }

    suspend fun sendTest(token: String, chatId: String): Result<Unit> {
        return post(token, chatId, "✅ <b>Notif to Telegram</b>\nConnection successful.")
    }

    // --- Formatting ---

    private fun formatBatch(items: List<NotificationEntity>, includeAppName: Boolean): String {
        val displayItems = items.take(MAX_DISPLAY_ITEMS)
        val overflow = items.size - displayItems.size

        return buildString {
            if (items.size > 1) {
                appendLine("📦 <b>${items.size} notifications</b>")
                appendLine()
            }

            displayItems.forEachIndexed { index, item ->
                append(formatItem(item, includeAppName))
                if (index < displayItems.lastIndex) {
                    appendLine()
                    appendLine(DIVIDER)
                    appendLine()
                }
            }

            if (overflow > 0) {
                appendLine()
                append("<i>+$overflow more not shown</i>")
            }
        }
    }

    private fun formatItem(item: NotificationEntity, includeAppName: Boolean): String {
        return buildString {
            if (includeAppName) {
                val time = timeFormat.format(Date(item.timestamp))
                appendLine("<b>${item.appLabel.escapeHtml()}</b> · $time")
            }
            appendLine("<b>${item.title.escapeHtml()}</b>")
            append(item.message.escapeHtml())
        }
    }

    // --- HTTP ---

    private suspend fun post(token: String, chatId: String, text: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "HTML")
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendMessage")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    // Parse Telegram's error description from the response body
                    val errorDescription = runCatching {
                        JSONObject(response.body?.string() ?: "").getString("description")
                    }.getOrDefault("HTTP ${response.code}")
                    Result.failure(Exception(errorDescription))
                }
            } catch (e: Exception) {
                // Re-throw coroutine cancellation — never swallow it
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    // --- Helpers ---

    // Escape user-generated content before embedding in Telegram HTML
    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    companion object {
        private const val MAX_DISPLAY_ITEMS = 10
        private const val DIVIDER = "———"
    }
}