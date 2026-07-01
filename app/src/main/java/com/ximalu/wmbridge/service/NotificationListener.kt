package com.ximalu.wmbridge.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.ximalu.wmbridge.data.BatchBuffer
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.matrix.MatrixClient
import com.ximalu.wmbridge.model.WeChatNotification
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WMBridge"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val BATCH_TRIGGER_COUNT = 3
        private const val FLUSH_INTERVAL_MS = 600_000L // 10 minutes

        // Key constants for WeChat notification extras
        private const val EXTRA_CONVERSATION_TITLE = "android.conversationTitle"
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
        private const val EXTRA_SUB_TEXT = "android.subText"
        private const val EXTRA_MESSAGES = "android.messages"

        // Notification category used by WeChat for group chats
        private const val CATEGORY_GROUP = "msg_group"
        private const val CATEGORY_SINGLE = "msg_single"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var config: Config
    private lateinit var client: MatrixClient
    private lateinit var buffer: BatchBuffer
    private var flushJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        config = Config(this)
        client = MatrixClient(config)
        buffer = BatchBuffer()

        // Start periodic flush timer
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushBatch("interval")
            }
        }

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
        scope.launch {
            val count = buffer.add(notification)
            Log.d(TAG, "Buffered notification #$count: ${notification.sender}: ${notification.content}")

            if (count >= BATCH_TRIGGER_COUNT) {
                flushBatch("count_trigger")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed on removal
    }

    private suspend fun flushBatch(reason: String) {
        val batch = buffer.flush()
        if (batch.isEmpty()) return

        Log.i(TAG, "Flushing ${batch.size} notification(s) (reason=$reason)")

        val result = client.sendBatch(batch)
        result.fold(
            onSuccess = {
                Log.i(TAG, "Batch sent successfully (${batch.size} items)")
            },
            onFailure = { error ->
                Log.e(TAG, "Batch send failed: ${error.message}")
                // TODO: retry logic / persistent queue
            }
        )
    }

    private fun parseWeChatNotification(sbn: StatusBarNotification): WeChatNotification? {
        val extras = sbn.notification.extras ?: return null

        val title = extras.getString(EXTRA_TITLE) ?: return null
        val text = extras.getString(EXTRA_TEXT)
            ?: extras.getString(EXTRA_SUB_TEXT)
            ?: return null
        val conversationTitle = extras.getString(EXTRA_CONVERSATION_TITLE)

        // Determine sender and group info from the notification structure
        val isGroup = sbn.notification.category == CATEGORY_GROUP
                || conversationTitle != null

        // In WeChat notifications:
        // - Single chat: title = sender name, text = message content
        // - Group chat: title = group name, conversationTitle = group name,
        //   text = "sender: message content" or just message content
        val sender: String
        val content: String
        val groupName: String?

        if (isGroup && conversationTitle != null) {
            groupName = conversationTitle
            // title might be the group name, text might be "sender: content"
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
