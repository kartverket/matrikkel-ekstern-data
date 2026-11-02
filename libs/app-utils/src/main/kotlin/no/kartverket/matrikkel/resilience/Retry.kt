package no.kartverket.matrikkel.resilience

import kotlinx.coroutines.delay

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T> retry(
    attempts: Int,
    stopRetryIf: (Throwable) -> Boolean = { false },
    baseDelay: Duration = 2.seconds,
    fn: () -> T,
): T {
    require(attempts > 0) { "attempts must be > 0" }
    require(!baseDelay.isNegative()) { "baseDelay must be >= 0" }
    var attempt = 0
    do {
        val result = runCatching {
            fn()
        }
        attempt++

        if (result.isSuccess) return result.getOrThrow()
        if (attempt == attempts) return result.getOrThrow()
        if (stopRetryIf(result.exceptionOrNull()!!)) return result.getOrThrow()

        delay(baseDelay * 2.0.pow(attempt))
    } while (true)
}
