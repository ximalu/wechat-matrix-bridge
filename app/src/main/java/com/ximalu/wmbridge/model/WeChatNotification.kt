package com.ximalu.wmbridge.model

data class WeChatNotification(
    val sender: String,
    val content: String,
    val groupName: String?,
    val timestamp: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "sender" to sender,
        "content" to content,
        "group_name" to groupName,
        "timestamp" to timestamp
    )
}
