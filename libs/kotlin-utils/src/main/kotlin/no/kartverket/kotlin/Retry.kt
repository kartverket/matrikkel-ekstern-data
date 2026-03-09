package no.kartverket.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

fun <T> retry(
    attempts: Int,
    stopRetryIf: (Throwable) -> Boolean = { false },
    fn: () -> T,
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

        runBlocking {
            delay((2.0.pow(attempt + 1)).seconds)
        }
    } while (true)
}
