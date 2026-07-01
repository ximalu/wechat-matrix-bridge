package com.ximalu.wmbridge.data

import com.ximalu.wmbridge.model.WeChatNotification
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatchBuffer {

    private val buffer = mutableListOf<WeChatNotification>()
    private val mutex = Mutex()

    suspend fun add(notification: WeChatNotification): Int = mutex.withLock {
        buffer.add(notification)
        buffer.size
    }

    suspend fun flush(): List<WeChatNotification> = mutex.withLock {
        val batch = buffer.toList()
        buffer.clear()
        batch
    }

    suspend val size: Int get() = mutex.withLock { buffer.size }
}
