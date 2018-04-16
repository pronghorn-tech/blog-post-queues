package tech.pronghorn.blog.queues.scheduler

import tech.pronghorn.blog.queues.queues.SuspendingQueue
import kotlin.coroutines.experimental.startCoroutine

class Writer(val count: Long,
             val queue: SuspendingQueue<Long>) {
    fun launch() {
        suspend { run() }.startCoroutine(SimpleCoroutine())
    }

    suspend fun run() {
        var x = 0L
        while (x < count) {
            queue.add(x)
            x += 1
        }
    }
}
