package com.ximalu.wmbridge.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ximalu.wmbridge.model.MessageEntry
import com.ximalu.wmbridge.model.WeChatNotification

/**
 * 消息历史记录持久化存储。
 * 跨进程访问（:bridge 进程写入，主进程读取），使用 MODE_MULTI_PROCESS。
 */
class MessageHistory(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS)
    private val gson = Gson()

    /** 读取全部历史记录，按时间倒序（最新的在前） */
    fun getAll(): List<MessageEntry> {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<Map<String, Any?>>>() {}.type
        val rawList: List<Map<String, Any?>> = try {
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
        return rawList.map { MessageEntry.fromMap(it) }
            .sortedByDescending { it.timestamp }
    }

    /** 添加一条新记录（状态为 PENDING） */
    fun add(
        notification: WeChatNotification,
        status: MessageEntry.Status = MessageEntry.Status.PENDING
    ) {
        val list = getAllMutable()
        val entry = MessageEntry(
            id = generateId(notification),
            sender = notification.sender,
            content = notification.content,
            groupName = notification.groupName,
            timestamp = notification.timestamp,
            status = status
        )
        list.add(0, entry) // 最新的在前面
        saveList(list)
    }

    /** 批量更新状态（flush 成功后调用） */
    fun markSent(ids: List<String>) {
        val list = getAllMutable()
        var changed = false
        for (entry in list) {
            if (entry.id in ids && entry.status != MessageEntry.Status.SENT) {
                entry.status = MessageEntry.Status.SENT
                changed = true
            }
        }
        if (changed) saveList(list)
    }

    /** 批量标记失败 */
    fun markFailed(ids: List<String>) {
        val list = getAllMutable()
        var changed = false
        for (entry in list) {
            if (entry.id in ids && entry.status != MessageEntry.Status.FAILED) {
                entry.status = MessageEntry.Status.FAILED
                changed = true
            }
        }
        if (changed) saveList(list)
    }

    /** 删除所有记录 */
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /** 限制记录数量，防止无限增长 */
    fun trimToSize(maxSize: Int = MAX_ENTRIES) {
        val list = getAllMutable()
        if (list.size > maxSize) {
            saveList(list.take(maxSize).toMutableList())
        }
    }

    /** 获取未发送的数量（PENDING + FAILED） */
    fun getPendingCount(): Int {
        return getAll().count { it.status != MessageEntry.Status.SENT }
    }

    // ── Private ──

    private fun getAllMutable(): MutableList<MessageEntry> {
        return getAll().toMutableList()
    }

    private fun saveList(list: MutableList<MessageEntry>) {
        val rawList = list.map { it.toMap() }
        val json = gson.toJson(rawList)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    private fun generateId(notification: WeChatNotification): String {
        val raw = "${notification.timestamp}_${notification.sender}_${notification.content}_${notification.groupName}"
        return raw.hashCode().toLong().toString(16)
    }

    companion object {
        private const val PREF_NAME = "wmbridge_message_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 500
    }
}
