package com.ximalu.wmbridge.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.ximalu.wmbridge.data.Config

/**
 * ContentProvider 用于跨进程通信。
 * 当 Service 运行在 :bridge 进程时，
 * UI 进程通过 ContentProvider 查询/触发 Service 侧操作。
 *
 * URI 约定:
 * - content://com.ximalu.wmbridge.bridge/status → 服务运行状态
 * - content://com.ximalu.wmbridge.bridge/trigger_flush → 触发立即发送
 */
class BridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Log.i(TAG, "BridgeProvider created in process: ${android.os.Process.myPid()}")
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        return when {
            uri.toString().endsWith("/status") -> {
                val running = ForegroundService.isServiceRunning<ForegroundService>(
                    context ?: return null
                )
                MatrixCursor(arrayOf("running")).apply {
                    addRow(arrayOf(if (running) 1 else 0))
                }
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<String>?
    ): Int {
        when {
            uri.toString().endsWith("/trigger_flush") -> {
                Log.i(TAG, "Flush triggered via IPC")
                // TODO: trigger batch flush in NotificationListener
                return 1
            }
        }
        return 0
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String? = null

    companion object {
        private const val TAG = "WMBridge.Provider"
        const val AUTHORITY = "com.ximalu.wmbridge.bridge"
    }
}
