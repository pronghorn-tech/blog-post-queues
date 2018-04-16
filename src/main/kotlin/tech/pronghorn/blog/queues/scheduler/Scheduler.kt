package tech.pronghorn.blog.queues.scheduler

import tech.pronghorn.collections.RingBufferQueue
import kotlin.coroutines.experimental.Continuation

class Scheduler {
    private val ringBufferQueue  = RingBufferQueue<ReadyContinuation<Any>>(1024)

    @Suppress("UNCHECKED_CAST")
    fun <T> scheduleContinuation(continuation: Continuation<T>,
                                 value: T) {
        ringBufferQueue.offer(ReadyContinuation(continuation, value) as ReadyContinuation<Any>)
    }

    @Suppress("UNCHECKED_CAST")
    fun run() {
        var next = ringBufferQueue.poll()
        while(next != null){
            next.resume()
            next = ringBufferQueue.poll()
        }
    }
}
