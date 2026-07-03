package com.ximalu.wmbridge.data

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 日志条目。
 *
 * @property id 自增序列号，用于排序 & ContentProvider 分页
 * @property timestamp 事件发生时间戳（System.currentTimeMillis）
 * @property level 日志级别：DEBUG / INFO / WARN / ERROR
 * @property tag 来源标签（如 NotificationListener、MatrixClient）
 * @property message 日志消息
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)

/**
 * 线程安全的环形日志缓冲区，运行在 [:bridge] 进程中。
 *
 * BridgeProvider 通过 Binder IPC 将日志内容提供给主进程 UI。
 * 最多保留 maxSize 条，超出自动丢弃最旧的。
 */
class LogBuffer(private val maxSize: Int = 500) {

    private val buffer = ArrayDeque<LogEntry>(maxSize)
    private var nextId = 1L
    private val lock = ReentrantLock()

    fun add(level: String, tag: String, message: String) {
        lock.withLock {
            val entry = LogEntry(nextId++, System.currentTimeMillis(), level, tag, message)
            if (buffer.size >= maxSize) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    fun getAll(): List<LogEntry> = lock.withLock { buffer.toList() }

    fun getSince(id: Long): List<LogEntry> = lock.withLock {
        buffer.dropWhile { it.id <= id }
    }

    fun clear() = lock.withLock { buffer.clear() }

    fun size(): Int = lock.withLock { buffer.size }

    companion object {
        @Volatile
        private var instance: LogBuffer? = null

        @JvmStatic
        fun getInstance(): LogBuffer {
            return instance ?: synchronized(this) {
                instance ?: LogBuffer().also { instance = it }
            }
        }
    }
}
