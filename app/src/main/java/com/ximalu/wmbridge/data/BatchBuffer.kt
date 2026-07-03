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

    /** Flush up to [maxSize] items; remaining stay in buffer. */
    suspend fun flush(maxSize: Int = Int.MAX_VALUE): List<WeChatNotification> = mutex.withLock {
        val count = minOf(maxSize, buffer.size)
        val batch = buffer.take(count)
        buffer.subList(0, count).clear()
        batch
    }

    /** Flush all items. */
    suspend fun flushAll(): List<WeChatNotification> = mutex.withLock {
        val batch = buffer.toList()
        buffer.clear()
        batch
    }

    suspend fun size(): Int = mutex.withLock { buffer.size }
}
