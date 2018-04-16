package tech.pronghorn.blog.queues.queues

import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class FewerAllocationsSuspendingQueue<T>(capacity: Int) : SuspendingQueue<T> {
    private val underlying: Queue<T> = ArrayBlockingQueue(capacity)
    private var fullWaiter: Continuation<Unit>? = null
    private var emptyWaiter: Continuation<T>? = null

    override suspend fun add(value: T): Boolean {
        val emptyWaiter = this.emptyWaiter
        if (emptyWaiter != null) {
            this.emptyWaiter = null
            emptyWaiter.resume(value)
            return true
        }
        else if(underlying.offer(value)){
            return true
        }
        return suspendAdd(value)
    }

    private tailrec suspend fun suspendAdd(value: T): Boolean {
        suspendCoroutine<Unit> { fullWaiter = it }
        if(!underlying.offer(value)) {
            return suspendAdd(value)
        }
        return true
    }

    override suspend fun take(): T {
        val result = underlying.poll()
        if (result != null) {
            val fullWaiter = this.fullWaiter
            if (fullWaiter != null) {
                this.fullWaiter = null
                fullWaiter.resume(Unit)
            }
            return result
        }
        return suspendTake()
    }

    private suspend fun suspendTake(): T {
        return suspendCoroutine { emptyWaiter = it }
    }
}
