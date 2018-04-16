package tech.pronghorn.blog.queues.queues

import tech.pronghorn.blog.queues.scheduler.Scheduler
import java.util.Queue
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class SchedulerSuspendingQueue<T>(val scheduler: Scheduler,
                                  val underlying: Queue<T>) : SuspendingQueue<T> {
    private var fullWaiter: Continuation<Unit>? = null
    private var emptyWaiter: Continuation<T>? = null

    override suspend fun add(value: T): Boolean {
        val fullWaiter = this.emptyWaiter
        if (fullWaiter != null) {
            this.emptyWaiter = null
            scheduler.scheduleContinuation(fullWaiter, value)
            return true
        }
        else if (underlying.offer(value)) {
            return true
        }
        return suspendAdd(value)
    }

    private tailrec suspend fun suspendAdd(value: T): Boolean {
        suspendCoroutine<Unit> { fullWaiter = it }
        if (!underlying.offer(value)) {
            return suspendAdd(value)
        }
        return true
    }

    private suspend fun suspendTake(): T {
        return suspendCoroutine { emptyWaiter = it }
    }

    override suspend fun take(): T {
        val result = underlying.poll()
        if (result != null) {
            val fullWaiter = this.fullWaiter
            if (fullWaiter != null) {
                this.fullWaiter = null
                scheduler.scheduleContinuation(fullWaiter, Unit)
            }
            return result
        }

        return suspendTake()
    }
}
