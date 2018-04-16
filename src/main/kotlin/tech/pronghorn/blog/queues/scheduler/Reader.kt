package tech.pronghorn.blog.queues.scheduler

import tech.pronghorn.blog.queues.queues.SuspendingQueue
import kotlin.coroutines.experimental.startCoroutine

class Reader(val count: Long,
             val queue: SuspendingQueue<Long>) {
    var total = 0L

    fun launch() {
        suspend { run() }.startCoroutine(SimpleCoroutine())
    }

    suspend fun run() {
        var received = 0L
        while (received < count) {
            val value = queue.take()
            total += value
            received += 1
        }
    }
}
