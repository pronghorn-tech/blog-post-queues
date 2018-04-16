package tech.pronghorn.blog.queues.queues

interface SuspendingQueue<T> {
    suspend fun take(): T

    suspend fun add(value: T): Boolean
}
