package com.ximalu.wmbridge.matrix

import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.model.WeChatNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.UUID
import java.util.concurrent.TimeUnit

class MatrixClient(private val config: Config) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun sendBatch(notifications: List<WeChatNotification>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!config.isConfigured) {
                    return@withContext Result.failure(
                        IllegalStateException("Matrix not configured")
                    )
                }

                val payload = buildBatchPayload(notifications)
                val txnId = UUID.randomUUID().toString()
                val url = "${config.matrixHomeserver}/_matrix/client/v3/rooms/" +
                        "${config.matrixRoomId}/send/m.room.message/$txnId"

                val body = gson.toJson(payload).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${config.matrixToken}")
                    .header("Content-Type", "application/json")
                    .put(body)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        RuntimeException(
                            "Matrix send failed: HTTP ${response.code} ${response.body?.string()}"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun buildBatchPayload(notifications: List<WeChatNotification>): Map<String, Any> {
        val items = notifications.map { it.toMap() }
        return mapOf(
            "msgtype" to "m.text",
            "body" to buildTextSummary(notifications),
            "content" to mapOf(
                "format" to "org.matrix.custom.html",
                "formatted_body" to buildHtmlSummary(notifications)
            ),
            "com.ximalu.wmbridge" to mapOf(
                "type" to "wechat_notification_batch",
                "version" to 1,
                "count" to notifications.size,
                "items" to items
            )
        )
    }

    private fun buildTextSummary(notifications: List<WeChatNotification>): String {
        return if (notifications.size == 1) {
            val n = notifications[0]
            buildString {
                append("[微信] ")
                if (n.groupName != null) append("(${n.groupName}) ")
                append("${n.sender}: ${n.content}")
            }
        } else {
            "[微信] ${notifications.size} 条新消息"
        }
    }

    private fun buildHtmlSummary(notifications: List<WeChatNotification>): String {
        return notifications.joinToString("<br>") { n ->
            buildString {
                append("<b>")
                if (n.groupName != null) append("[${n.groupName}] ")
                append("${n.sender}</b>: ${n.content}")
            }
        }
    }
}
