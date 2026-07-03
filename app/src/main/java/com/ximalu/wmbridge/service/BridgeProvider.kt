package com.ximalu.wmbridge.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.ximalu.wmbridge.data.LogBuffer
import com.ximalu.wmbridge.data.MessageHistory
import com.ximalu.wmbridge.model.MessageEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ContentProvider 用于跨进程通信。
 *
 * 当 Service 运行在 [:bridge] 进程时，
 * UI 进程通过 ContentProvider 查询/触发 Service 侧操作。
 *
 * URI 约定:
 * - content://com.ximalu.wmbridge.bridge/status → 服务运行状态 (running: 0|1)
 * - content://com.ximalu.wmbridge.bridge/logs → 全部日志 (level, tag, msg, time)
 * - content://com.ximalu.wmbridge.bridge/logs_since/{id} → 指定 id 之后的增量日志
 * - content://com.ximalu.wmbridge.bridge/history → 消息记录 (全部字段)
 * - content://com.ximalu.wmbridge.bridge/history_count → 消息统计 (total, pending, sent, failed)
 * - content://com.ximalu.wmbridge.bridge/trigger_flush → 触发立即发送 (update)
 * - content://com.ximalu.wmbridge.bridge/clear_logs → 清空日志 (update)
 * - content://com.ximalu.wmbridge.bridge/clear_history → 清空消息记录 (detete)
 */
class BridgeProvider : ContentProvider() {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(): Boolean {
        Log.i(TAG, "BridgeProvider created in process: ${android.os.Process.myPid()}")
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val path = uri.path ?: return null
        return when {
            path.endsWith("/status") -> queryStatus()
            path.endsWith("/logs") -> queryLogs()
            path.contains("/logs_since/") -> queryLogsSince(extractId(path, "/logs_since/"))
            path.endsWith("/history") -> queryHistory()
            path.endsWith("/history_count") -> queryHistoryCount()
            else -> null
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<String>?
    ): Int {
        val path = uri.path ?: return 0
        return when {
            path.endsWith("/trigger_flush") -> {
                Log.i(TAG, "Flush triggered via IPC")
                // TODO: trigger batch flush in NotificationListener
                1
            }
            path.endsWith("/clear_logs") -> {
                LogBuffer.getInstance().clear()
                Log.i(TAG, "Logs cleared via IPC")
                1
            }
            else -> 0
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val path = uri.path ?: return 0
        return when {
            path.endsWith("/clear_history") -> {
                val ctx = context ?: return 0
                MessageHistory(ctx).clear()
                Log.i(TAG, "History cleared via IPC")
                1
            }
            else -> 0
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun getType(uri: Uri): String? = null

    // ── Status ──

    private fun queryStatus(): Cursor {
        val running = ForegroundService.isServiceRunning<ForegroundService>(
            context ?: return MatrixCursor(arrayOf("running")).apply { addRow(arrayOf(0)) }
        )
        return MatrixCursor(arrayOf("running")).apply {
            addRow(arrayOf(if (running) 1 else 0))
        }
    }

    // ── Logs ──

    private fun queryLogs(): Cursor {
        val entries = LogBuffer.getInstance().getAll()
        return buildLogCursor(entries)
    }

    private fun queryLogsSince(id: Long): Cursor {
        val entries = LogBuffer.getInstance().getSince(id)
        return buildLogCursor(entries)
    }

    private fun buildLogCursor(entries: List<com.ximalu.wmbridge.data.LogEntry>): Cursor {
        val columns = arrayOf("_id", "time", "level", "tag", "msg")
        val cursor = MatrixCursor(columns)
        for (e in entries) {
            cursor.addRow(arrayOf(
                e.id,
                dateFormat.format(Date(e.timestamp)),
                e.level,
                e.tag,
                e.message
            ))
        }
        return cursor
    }

    // ── History ──

    private fun queryHistory(): Cursor {
        val ctx = context ?: return MatrixCursor(arrayOf("_id", "json")).apply { }
        val entries = MessageHistory(ctx).getAll()
        val columns = arrayOf("_id", "sender", "content", "group", "time", "status")
        val cursor = MatrixCursor(columns)
        for (e in entries) {
            cursor.addRow(arrayOf(
                e.id,
                e.sender,
                e.content,
                e.groupName ?: "",
                dateFormat.format(Date(e.timestamp)),
                when (e.status) {
                    MessageEntry.Status.PENDING -> "pending"
                    MessageEntry.Status.SENT -> "sent"
                    MessageEntry.Status.FAILED -> "failed"
                }
            ))
        }
        return cursor
    }

    private fun queryHistoryCount(): Cursor {
        val ctx = context ?: return MatrixCursor(arrayOf("total", "pending", "sent", "failed")).apply {
            addRow(arrayOf(0, 0, 0, 0))
        }
        val entries = MessageHistory(ctx).getAll()
        val total = entries.size
        val pending = entries.count { it.status == MessageEntry.Status.PENDING }
        val sent = entries.count { it.status == MessageEntry.Status.SENT }
        val failed = entries.count { it.status == MessageEntry.Status.FAILED }
        return MatrixCursor(arrayOf("total", "pending", "sent", "failed")).apply {
            addRow(arrayOf(total, pending, sent, failed))
        }
    }

    // ── Helpers ──

    private fun extractId(path: String, prefix: String): Long {
        return path.substringAfter(prefix).toLongOrNull() ?: 0L
    }

    companion object {
        private const val TAG = "WMBridge.Provider"
        const val AUTHORITY = "com.ximalu.wmbridge.bridge"
    }
}
