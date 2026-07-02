package com.ximalu.wmbridge.model

/**
 * 微信通知的持久化记录，用于「消息记录」Tab 展示
 */
data class MessageEntry(
    val id: String,
    val sender: String,
    val content: String,
    val groupName: String?,
    val timestamp: Long,
    var status: Status = Status.PENDING
) {
    enum class Status {
        /** 已收集，尚未发送 */
        PENDING,
        /** 已成功发送到 Matrix */
        SENT,
        /** 发送失败 */
        FAILED
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "sender" to sender,
        "content" to content,
        "group_name" to groupName,
        "timestamp" to timestamp,
        "status" to status.name
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MessageEntry {
            return MessageEntry(
                id = (map["id"] as? String) ?: "",
                sender = (map["sender"] as? String) ?: "",
                content = (map["content"] as? String) ?: "",
                groupName = map["group_name"] as? String,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                status = try {
                    Status.valueOf((map["status"] as? String) ?: "PENDING")
                } catch (_: Exception) { Status.PENDING }
            )
        }
    }
}
