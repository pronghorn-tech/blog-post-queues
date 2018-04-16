package tech.pronghorn.blog.queues.scheduler

import kotlin.coroutines.experimental.Continuation

class ReadyContinuation<T>(var continuation: Continuation<T>? = null,
                           var value: T? = null) {
    fun resume() = continuation!!.resume(value!!)
}
