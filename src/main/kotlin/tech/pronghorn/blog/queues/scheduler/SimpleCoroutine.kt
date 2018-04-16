package tech.pronghorn.blog.queues.scheduler

import kotlin.coroutines.experimental.*

class SimpleCoroutine<T> : Continuation<T> {
    override val context: CoroutineContext = EmptyCoroutineContext

    override fun resume(value: T) = Unit

    override fun resumeWithException(exception: Throwable) = Unit
}
