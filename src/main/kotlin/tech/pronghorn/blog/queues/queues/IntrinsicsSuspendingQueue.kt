package tech.pronghorn.blog.queues.queues

import tech.pronghorn.blog.queues.scheduler.Scheduler
import tech.pronghorn.collections.RingBufferQueue
import java.util.Queue
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineUninterceptedOrReturn

class IntrinsicsSuspendingQueue<T>(val scheduler: Scheduler,
                                   capacity: Int) : SuspendingQueue<T> {
    private val underlying: Queue<T> = RingBufferQueue(capacity)
    private var fullWaiter: Continuation<Unit>? = null
    private var emptyWaiter: Continuation<T>? = null

    private fun offer(value: T): Boolean {
        val fullWaiter = this.emptyWaiter
        if (fullWaiter != null) {
            this.emptyWaiter = null
            scheduler.scheduleContinuation(fullWaiter, value)
            return true
        }
        return underlying.offer(value)
    }

    override suspend fun add(value: T): Boolean {
        if (offer(value)) {
            return true
        }
        return suspendAdd(value)
    }

    private tailrec suspend fun suspendAdd(value: T): Boolean {
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            fullWaiter = continuation
            COROUTINE_SUSPENDED
        }
        if (underlying.offer(value)) {
            return true
        }
        return suspendAdd(value)
    }

    private suspend fun suspendTake(): T {
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            emptyWaiter = continuation
            COROUTINE_SUSPENDED
        }
    }

    private fun poll(): T? {
        val result = underlying.poll()
        if (result != null) {
            val fullWaiter = this.fullWaiter
            if (fullWaiter != null) {
                this.fullWaiter = null
                scheduler.scheduleContinuation(fullWaiter, Unit)
            }
        }
        return result
    }

    override suspend fun take(): T {
        val result = poll()
        if (result != null) {
            return result
        }
        return suspendTake()
    }
}
