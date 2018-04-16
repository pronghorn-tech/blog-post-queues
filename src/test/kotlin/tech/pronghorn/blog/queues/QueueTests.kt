package tech.pronghorn.blog.queues

import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import org.junit.jupiter.api.Test
import tech.pronghorn.blog.queues.queues.*
import tech.pronghorn.blog.queues.scheduler.*
import tech.pronghorn.collections.RingBufferQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class QueueTests {
    var total = 0L

    val WARMUP_COUNT = 100000000L
    val WARMUP_ITERATIONS = 10
    val PROPER_COUNT = 100000000L
    val PROPER_ITERATIONS = 20

    @Test
    fun kotlinChannel() {
        testQueue(
                "Kotlin Channel",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeChannel(it) }
        )
    }

    @Test
    fun naive() {
        val queue = NaiveSuspendingQueue<Long>(1024)
        testQueue(
                "Naive",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeBasic(queue, it) }
        )
    }

    @Test
    fun fewerAllocations() {
        val queue = FewerAllocationsSuspendingQueue<Long>(1024)
        testQueue(
                "Fewer Allocations",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeBasic(queue, it) }
        )
    }

    @Test
    fun scheduler() {
        val scheduler = Scheduler()
        val underlying = ArrayBlockingQueue<Long>(1024)
        val queue = SchedulerSuspendingQueue(scheduler, underlying)
        testQueue(
                "Scheduler",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeScheduled(scheduler, queue, it) }
        )
    }

    @Test
    fun schedulerWithRingBuffer() {
        val scheduler = Scheduler()
        val underlying = RingBufferQueue<Long>(1024)
        val queue = SchedulerSuspendingQueue(scheduler, underlying)
        testQueue(
                "Scheduler w Ring Buffer",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeScheduled(scheduler, queue, it) }
        )
    }

    @Test
    fun intrinsics() {
        val scheduler = Scheduler()
        val queue = IntrinsicsSuspendingQueue<Long>(scheduler, 1024)
        testQueue(
                "Intrinsics",
                WARMUP_COUNT,
                WARMUP_ITERATIONS,
                PROPER_COUNT,
                PROPER_ITERATIONS,
                { timeScheduled(scheduler, queue, it) }
        )
    }

    fun timeChannel(count: Long): Long {
        val channel = Channel<Long>(1024)
        val context = newSingleThreadContext("workerThread")
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        val pre = System.currentTimeMillis()
        launch(context) {
            var received = 0L
            while (received < count) {
                val value = channel.receive()
                total += value
                received += 1
            }
            lock.withLock(condition::signal)
        }

        launch(context) {
            var x = 0L
            while (x < count) {
                channel.send(x)
                x += 1
            }
        }

        lock.withLock(condition::await)
        val post = System.currentTimeMillis()
        context.close()
        return post - pre
    }

    fun timeBasic(queue: SuspendingQueue<Long>,
                  count: Long): Long {
        val context = newSingleThreadContext("workerThread")
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        val pre = System.currentTimeMillis()
        launch(context) {
            var received = 0L
            while (received < count) {
                val value = queue.take()
                total += value
                received += 1
            }
            lock.withLock(condition::signal)
        }

        launch(context) {
            var x = 0L
            while (x < count) {
                queue.add(x)
                x += 1
            }
        }

        lock.withLock(condition::await)
        val post = System.currentTimeMillis()
        context.close()
        return post - pre
    }

    fun timeScheduled(scheduler: Scheduler,
                      queue: SuspendingQueue<Long>,
                      count: Long): Long {
        val reader = Reader(count, queue)
        val writer = Writer(count, queue)

        val pre = System.currentTimeMillis()
        reader.launch()
        writer.launch()
        scheduler.run()
        val post = System.currentTimeMillis()
        total += reader.total
        return post - pre
    }

    fun testQueue(testName: String,
                  warmupCount: Long,
                  warmupIterations: Int,
                  properCount: Long,
                  properIterations: Int,
                  testFunction: (Long) -> Long) {
        // warm-up
        var iteration = 0
        while (iteration < warmupIterations) {
            val time = testFunction(warmupCount)
            println("$testName : Warm-up #${iteration + 1} took $time ms for $warmupCount, ${(warmupCount / time) / 1000.0} million per second")
            iteration += 1
        }

        // proper run
        iteration = 0
        var totalTime = 0L
        var minTime = Long.MAX_VALUE
        var maxTime = Long.MIN_VALUE
        while (iteration < properIterations) {
            val time = testFunction(properCount)
            totalTime += time
            minTime = Math.min(minTime, time)
            maxTime = Math.max(maxTime, time)
            println("$testName : Iteration #${iteration + 1} took $time ms for $properCount, ${(properCount / time) / 1000.0} million per second")
            iteration += 1
        }

        // calculate throughputs
        val avgTime = totalTime / properIterations
        val avgThroughputInMillions = (properCount / avgTime) / 1000.0
        val minThroughputInMillions = (properCount / maxTime) / 1000.0
        val maxThroughputInMillions = (properCount / minTime) / 1000.0
        println("$testName : Avg: $avgThroughputInMillions million per second, Min $minThroughputInMillions million per second, Max $maxThroughputInMillions million per second")

        // make sure we touch total so the compiler doesn't optimize it out
        println(total)
    }
}
