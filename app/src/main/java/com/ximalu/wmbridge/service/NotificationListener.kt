package com.ximalu.wmbridge.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ximalu.wmbridge.data.BatchBuffer
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.data.KeywordMode
import com.ximalu.wmbridge.data.LogBuffer
import com.ximalu.wmbridge.data.MaxBatchSize
import com.ximalu.wmbridge.data.MessageHistory
import com.ximalu.wmbridge.data.SendFrequency
import com.ximalu.wmbridge.matrix.MatrixClient
import com.ximalu.wmbridge.model.MessageEntry
import com.ximalu.wmbridge.model.WeChatNotification
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
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

        /** Force system to rebind this listener by toggling component state. */
        fun forceRebind(context: Context) {
            val cn = ComponentName(context, NotificationListener::class.java)
            context.packageManager.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            context.packageManager.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var config: Config
    private lateinit var client: MatrixClient
    private lateinit var buffer: BatchBuffer
    private lateinit var history: MessageHistory
    private lateinit var logger: LogBuffer
    private var flushJob: Job? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        config = Config(this)
        client = MatrixClient(config)
        buffer = BatchBuffer()
        history = MessageHistory(this)
        logger = LogBuffer.getInstance()

        logger.add("INFO", TAG, "NotificationListener onCreate — pid=${android.os.Process.myPid()}")
        logger.add("INFO", TAG, "NLS enabled check: ${isNlsEnabled()}")
        logConfigSnapshot("onCreate")

        restartFlushTimer()
        Log.i(TAG, "NotificationListener started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.add("INFO", TAG, "onStartCommand — action=${intent?.action} flags=$flags startId=$startId")
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        logger.add("INFO", TAG, "onListenerConnected — listener is now active")
        Log.i(TAG, "NotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        logger.add("WARN", TAG, "onListenerDisconnected — attempting rebind")
        Log.w(TAG, "NotificationListener disconnected, requesting rebind")
        // Rebind using component toggle (more reliable than requestRebind alone)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, NotificationListener::class.java))
            } catch (_: Exception) {}
        }
        forceRebind(this)
    }

    override fun onDestroy() {
        logger.add("INFO", TAG, "NotificationListener onDestroy")
        flushJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ts = timeFormat.format(Date(sbn.postTime))
        val key = "${sbn.packageName}:${sbn.id}"

        logger.add("DEBUG", TAG, "onNotificationPosted — pkg=${sbn.packageName} id=${sbn.id} key=$key ts=$ts")

        // Step 1: Package check
        if (sbn.packageName != WECHAT_PACKAGE) {
            logger.add("DEBUG", TAG, "→ SKIP: not WeChat (${sbn.packageName})")
            return
        }
        logger.add("INFO", TAG, "→ WeChat notification received (key=$key)")

        // Step 2: Service enabled check
        val enabled = config.serviceEnabled
        logger.add("DEBUG", TAG, "→ serviceEnabled=$enabled")
        if (!enabled) {
            logger.add("WARN", TAG, "→ DROP: service not enabled (key=$key)")
            return
        }

        // Step 3: Parse notification
        val notification = parseWeChatNotification(sbn)
        if (notification == null) {
            logger.add("WARN", TAG, "→ DROP: parseWeChatNotification returned null (key=$key)")
            logNotificationExtras(sbn)
            return
        }
        logger.add("INFO", TAG, "→ Parsed: sender=${notification.sender} content=${truncate(notification.content)} group=${notification.groupName ?: "(none)"}")

        // Step 4: Keyword filtering
        if (!matchesKeywords(notification)) {
            logger.add("DEBUG", TAG, "→ SKIP: filtered by keyword (sender=${notification.sender})")
            return
        }

        // Step 5: Buffer and persist
        scope.launch {
            val count = buffer.add(notification)
            history.add(notification, MessageEntry.Status.PENDING)
            logger.add("INFO", TAG, "→ Buffered #$count: ${notification.sender}: ${truncate(notification.content)}")

            // Get current send frequency
            val freq = try {
                SendFrequency.valueOf(config.sendFrequency)
            } catch (_: Exception) { SendFrequency.MIN_10 }

            // PER_MESSAGE: flush immediately
            if (freq == SendFrequency.PER_MESSAGE) {
                logger.add("DEBUG", TAG, "→ PER_MESSAGE mode, flushing immediately")
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
        val result = if (mode == KeywordMode.INCLUDE) matchesAny else !matchesAny
        logger.add("DEBUG", TAG, "→ Keyword filter: mode=${mode.name} text=\"${truncate(text)}\" result=$result")
        return result
    }

    // ── Flush timer ──

    private fun restartFlushTimer() {
        flushJob?.cancel()
        val freq = try {
            SendFrequency.valueOf(config.sendFrequency)
        } catch (_: Exception) { SendFrequency.MIN_10 }

        if (freq == SendFrequency.PER_MESSAGE) {
            logger.add("DEBUG", TAG, "→ PER_MESSAGE mode, no flush timer needed")
            return
        }

        val intervalMs = freq.millis ?: SendFrequency.MIN_10.millis!!
        flushJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                flushBatch("interval")
            }
        }
        logger.add("INFO", TAG, "→ Flush timer set to ${freq.label} (${intervalMs}ms)")
        Log.i(TAG, "Flush timer set to ${freq.label} (${intervalMs}ms)")
    }

    private suspend fun flushBatch(reason: String) {
        if (!config.isConfigured) {
            val dropped = buffer.flushAll()
            if (dropped.isNotEmpty()) {
                logger.add("INFO", TAG, "→ Flush ($reason): dropped ${dropped.size} notification(s) (Matrix not configured)")
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
            logger.add("DEBUG", TAG, "→ Sending chunk of ${batch.size} (reason=$reason)")
            val result = client.sendBatch(batch)
            result.fold(
                onSuccess = {
                    history.markSent(ids)
                    sentCount += batch.size
                    logger.add("INFO", TAG, "→ Chunk sent OK (${batch.size} items)")
                },
                onFailure = { error ->
                    history.markFailed(ids)
                    logger.add("ERROR", TAG, "→ Chunk send FAILED: ${error.message}")
                    Log.e(TAG, "Chunk send failed: ${error.message}")
                    batch.forEach { buffer.add(it) }
                    shouldStop = true
                }
            )
        }
        if (sentCount > 0) {
            logger.add("INFO", TAG, "→ Flush complete: $sentCount notifications (reason=$reason)")
            Log.i(TAG, "Flushed $sentCount notifications (reason=$reason)")
        }
    }

    private fun genId(notification: WeChatNotification): String {
        val raw = "${notification.timestamp}_${notification.sender}_${notification.content}_${notification.groupName}"
        return raw.hashCode().toLong().toString(16)
    }

    // ── Parse WeChat notification ──

    private fun parseWeChatNotification(sbn: StatusBarNotification): WeChatNotification? {
        val extras = sbn.notification.extras ?: run {
            logger.add("WARN", TAG, "→ parse: extras is null")
            return null
        }

        val title = extras.getString(EXTRA_TITLE)
        val text = extras.getString(EXTRA_TEXT)
            ?: extras.getString(EXTRA_SUB_TEXT)
        val conversationTitle = extras.getString(EXTRA_CONVERSATION_TITLE)

        logger.add("DEBUG", TAG, "→ parse: title=$title text=$text conversationTitle=$conversationTitle")
        logger.add("DEBUG", TAG, "→ parse: category=${sbn.notification.category}")

        if (title == null) {
            logger.add("WARN", TAG, "→ parse FAILED: title is null")
            return null
        }
        if (text == null) {
            logger.add("WARN", TAG, "→ parse FAILED: text is null")
            return null
        }

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

        logger.add("DEBUG", TAG, "→ parse OK: sender=$sender content=${truncate(content)} isGroup=$isGroup")
        return WeChatNotification(
            sender = sender.trim(),
            content = content.trim(),
            groupName = groupName?.trim(),
            timestamp = sbn.postTime
        )
    }

    // ── Diagnostics ──

    private fun logNotificationExtras(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val keys = extras.keySet().joinToString(", ")
        logger.add("WARN", TAG, "→ extras keys: $keys")
        for (key in listOf(EXTRA_TITLE, EXTRA_TEXT, EXTRA_SUB_TEXT, EXTRA_CONVERSATION_TITLE)) {
            val value = extras.getString(key)
            logger.add("DEBUG", TAG, "  $key = $value")
        }
        logger.add("DEBUG", TAG, "  category = ${sbn.notification.category}")
        logger.add("DEBUG", TAG, "  group = ${sbn.notification.group}")
    }

    private fun logConfigSnapshot(source: String) {
        logger.add("DEBUG", TAG, "Config snapshot ($source):")
        logger.add("DEBUG", TAG, "  serviceEnabled=${config.serviceEnabled}")
        logger.add("DEBUG", TAG, "  isConfigured=${config.isConfigured}")
        logger.add("DEBUG", TAG, "  sendFrequency=${config.sendFrequency}")
        logger.add("DEBUG", TAG, "  keywordMode=${config.keywordMode}")
        logger.add("DEBUG", TAG, "  keywords=\"${config.keywords}\"")
        logger.add("DEBUG", TAG, "  maxBatchSize=${config.maxBatchSize}")
        logger.add("DEBUG", TAG, "  showPersistentNotification=${config.showPersistentNotification}")
    }

    private fun isNlsEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { name ->
            val cn = android.content.ComponentName.unflattenFromString(name)
            cn != null && packageName == cn.packageName
        }
    }

    private fun truncate(s: String, maxLen: Int = 80): String {
        return if (s.length <= maxLen) s else s.take(maxLen) + "..."
    }

    private fun logNow(level: String, tag: String, msg: String) {
        logger.add(level, tag, msg)
    }
}
