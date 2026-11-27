package no.kartverket.matrikkel.resilience

import kotlinx.coroutines.delay

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface BackoffStrategy {
    fun getDelay(attempt: Int): Duration

    class Constant(val value: Duration) : BackoffStrategy {
        override fun getDelay(attempt: Int) = value
    }

    class Linear(val increment: Duration) : BackoffStrategy {
        override fun getDelay(attempt: Int) = increment * attempt
    }
    class Exponential(val base: Double) : BackoffStrategy {
        override fun getDelay(attempt: Int) = base.pow(attempt).seconds
    }
}

suspend fun <T> retry(
    attempts: Int,
    stopRetryIf: (Throwable) -> Boolean = { false },
    backoffStrategy: BackoffStrategy = BackoffStrategy.Exponential(2.0),
    fn: suspend () -> T,
): T {
    require(attempts > 0) { "attempts must be > 0" }
    var attempt = 0
    do {
        val result = runCatching {
            fn()
        }
        attempt++

        if (result.isSuccess) return result.getOrThrow()
        else if (attempt == attempts) return result.getOrThrow()
        else if (stopRetryIf(result.exceptionOrNull()!!)) return result.getOrThrow()

        delay(backoffStrategy.getDelay(attempt))
    } while (true)
}
