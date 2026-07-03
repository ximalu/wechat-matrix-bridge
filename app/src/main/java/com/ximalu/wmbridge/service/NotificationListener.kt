package com.ximalu.wmbridge.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ximalu.wmbridge.data.BatchBuffer
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.data.KeywordMode
import com.ximalu.wmbridge.data.MaxBatchSize
import com.ximalu.wmbridge.data.MessageHistory
import com.ximalu.wmbridge.data.SendFrequency
import com.ximalu.wmbridge.matrix.MatrixClient
import com.ximalu.wmbridge.model.MessageEntry
import com.ximalu.wmbridge.model.WeChatNotification
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WMBridge"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private const val EXTRA_CONVERSATION_TITLE = "android.conversationTitle"
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
        private const val EXTRA_SUB_TEXT = "android.subText"
        private const val EXTRA_MESSAGES = "android.messages"

        private const val CATEGORY_GROUP = "msg_group"
        private const val CATEGORY_SINGLE = "msg_single"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var config: Config
    private lateinit var client: MatrixClient
    private lateinit var buffer: BatchBuffer
    private lateinit var history: MessageHistory
    private var flushJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        config = Config(this)
        client = MatrixClient(config)
        buffer = BatchBuffer()
        history = MessageHistory(this)
        restartFlushTimer()
        Log.i(TAG, "NotificationListener started")
    }

    override fun onDestroy() {
        flushJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WECHAT_PACKAGE) return
        if (!config.serviceEnabled) return

        val notification = parseWeChatNotification(sbn) ?: return

        // Keyword filtering
        if (!matchesKeywords(notification)) {
            Log.d(TAG, "Filtered by keyword: ${notification.sender}: ${notification.content}")
            return
        }

        scope.launch {
            val count = buffer.add(notification)
            history.add(notification, MessageEntry.Status.PENDING)
            Log.d(TAG, "Buffered #$count: ${notification.sender}: ${notification.content}")

            // Get current send frequency
            val freq = try {
                SendFrequency.valueOf(config.sendFrequency)
            } catch (_: Exception) { SendFrequency.MIN_10 }

            // PER_MESSAGE: flush immediately
            if (freq == SendFrequency.PER_MESSAGE) {
                flushBatch("per_message")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed
    }

    // ── Keyword filtering ──

    private fun matchesKeywords(notification: WeChatNotification): Boolean {
        val mode = try {
            KeywordMode.valueOf(config.keywordMode)
        } catch (_: Exception) { KeywordMode.OFF }
        if (mode == KeywordMode.OFF) return true

        val kwList = config.keywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (kwList.isEmpty()) return true

        val text = buildString {
            append(notification.sender)
            append(" ")
            append(notification.content)
            if (notification.groupName != null) {
                append(" ")
                append(notification.groupName)
            }
        }

        val matchesAny = kwList.any { text.contains(it) }
        return if (mode == KeywordMode.INCLUDE) matchesAny else !matchesAny
    }

    // ── Flush timer ──

    private fun restartFlushTimer() {
        flushJob?.cancel()
        val freq = try {
            SendFrequency.valueOf(config.sendFrequency)
        } catch (_: Exception) { SendFrequency.MIN_10 }

        // PER_MESSAGE doesn't need a timer — flush happens on each notification
        if (freq == SendFrequency.PER_MESSAGE) return

        val intervalMs = freq.millis ?: SendFrequency.MIN_10.millis!!
        flushJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                flushBatch("interval")
            }
        }
        Log.i(TAG, "Flush timer set to ${freq.label} (${intervalMs}ms)")
    }

    private suspend fun flushBatch(reason: String) {
        // If Matrix not configured, just drain buffer silently
        if (!config.isConfigured) {
            val dropped = buffer.flushAll()
            if (dropped.isNotEmpty()) {
                Log.i(TAG, "Dropped ${dropped.size} notification(s) (Matrix not configured)")
            }
            return
        }

        val maxSize = try {
            MaxBatchSize.valueOf(config.maxBatchSize).size
        } catch (_: Exception) { MaxBatchSize.SIZE_20.size }

        var sentCount = 0
        var shouldStop = false
        while (!shouldStop) {
            val batch = buffer.flush(maxSize)
            if (batch.isEmpty()) break

            val ids = batch.map { genId(it) }
            Log.d(TAG, "Sending chunk of ${batch.size} (reason=$reason)")
            val result = client.sendBatch(batch)
            result.fold(
                onSuccess = {
                    history.markSent(ids)
                    sentCount += batch.size
                },
                onFailure = { error ->
                    history.markFailed(ids)
                    Log.e(TAG, "Chunk send failed: ${error.message}")
                    batch.forEach { buffer.add(it) }
                    shouldStop = true
                }
            )
        }
        if (sentCount > 0) {
            Log.i(TAG, "Flushed $sentCount notifications (reason=$reason)")
        }
    }

    private fun genId(notification: WeChatNotification): String {
        val raw = "${notification.timestamp}_${notification.sender}_${notification.content}_${notification.groupName}"
        return raw.hashCode().toLong().toString(16)
    }

    // ── Parse WeChat notification ──

    private fun parseWeChatNotification(sbn: StatusBarNotification): WeChatNotification? {
        val extras = sbn.notification.extras ?: return null

        val title = extras.getString(EXTRA_TITLE) ?: return null
        val text = extras.getString(EXTRA_TEXT)
            ?: extras.getString(EXTRA_SUB_TEXT)
            ?: return null
        val conversationTitle = extras.getString(EXTRA_CONVERSATION_TITLE)

        val isGroup = sbn.notification.category == CATEGORY_GROUP
                || conversationTitle != null

        val sender: String
        val content: String
        val groupName: String?

        if (isGroup && conversationTitle != null) {
            groupName = conversationTitle
            val textParts = text.split(": ", limit = 2)
            if (textParts.size == 2) {
                sender = textParts[0]
                content = textParts[1]
            } else {
                sender = title ?: "群聊"
                content = text
            }
        } else {
            sender = title
            content = text
            groupName = null
        }

        return WeChatNotification(
            sender = sender.trim(),
            content = content.trim(),
            groupName = groupName?.trim(),
            timestamp = sbn.postTime
        )
    }
}
