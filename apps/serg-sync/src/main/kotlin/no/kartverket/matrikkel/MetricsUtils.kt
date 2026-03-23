package no.kartverket.matrikkel

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Timer as MetricsTimer
import kotlinx.coroutines.runBlocking
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun <T : Number> refreshingGauge(
    name: String,
    period: Duration = 60.seconds,
    fn: suspend () -> T
): Timer {
    var value: T = runBlocking { fn() }
    Gauge.builder(name) { value }
        .register(prometheusRegistry)

    return fixedRateTimer(
        name = "${name}_refresh",
        daemon = true,
        initialDelay = 0,
        period = period.inWholeMilliseconds
    ) {
        runBlocking {
            value = fn()
        }
    }
}

suspend fun <T> timed(name: String, fn: suspend () -> T): T {
    val timer = MetricsTimer.builder(name).register(prometheusRegistry)
    val start = System.nanoTime()

    return try {
        fn()
    } finally {
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}