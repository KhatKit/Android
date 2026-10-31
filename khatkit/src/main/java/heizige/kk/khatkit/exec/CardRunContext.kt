package heizige.kk.khatkit.exec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore

/** One invocation, also visible to synchronous JNI callbacks on its execution thread. */
class CardRunContext(val job: Job, private val lease: ExecutionLease? = null) {
    val id: String = java.util.UUID.randomUUID().toString()
    private val startedAt = System.nanoTime()
    private var waitingNanos = 0L
    private var waitingSince = 0L

    val cancelled: Boolean get() = !job.isActive
    val computationMillis: Long get() =
        (System.nanoTime() - startedAt - waitingNanos -
            if (waitingSince == 0L) 0L else System.nanoTime() - waitingSince) / 1_000_000

    /** User think time must not occupy a compute permit or outlive the owning job. */
    fun <T> awaitInteraction(block: suspend CoroutineScope.() -> T): T {
        waitingSince = System.nanoTime()
        return try {
            if (lease != null) lease.withoutPermit { runBlocking(job, block) } else runBlocking(job, block)
        } finally {
            waitingNanos += System.nanoTime() - waitingSince
            waitingSince = 0L
        }
    }

    companion object {
        val current = ThreadLocal<CardRunContext?>()

        fun <T> awaitInteraction(block: suspend CoroutineScope.() -> T): T {
            val run = current.get()
            return if (run != null) run.awaitInteraction(block) else runBlocking(block = block)
        }
    }
}

/** A permit can be lent to another card while this invocation waits for user input. */
class ExecutionLease(private val semaphore: Semaphore, private val job: Job) {
    private var held = false

    suspend fun acquire() {
        semaphore.acquire()
        held = true
    }

    fun release() {
        if (held) {
            held = false
            semaphore.release()
        }
    }

    fun <T> withoutPermit(block: () -> T): T {
        release()
        return try {
            block()
        } finally {
            runBlocking(job) { acquire() }
        }
    }
}
