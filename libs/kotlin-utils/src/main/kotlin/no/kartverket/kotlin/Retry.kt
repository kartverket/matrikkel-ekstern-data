package no.kartverket.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

fun <T> retry(n: Int, fn: () -> T): T {
    var attempt = 0
    do {
        var result = runCatching {
            fn()
        }
        attempt++

        if (result.isSuccess || attempt == n) return result.getOrThrow()

        runBlocking {
            delay((2.0.pow(attempt + 1)).seconds)
        }
    } while (true)
}