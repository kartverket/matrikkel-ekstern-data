package no.kartverket.matrikkel

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Timer as MetricsTimer
import kotlinx.coroutines.runBlocking
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private class GuageState(@Volatile var value: Double? = null)
fun <T : Number> refreshingGauge(
    name: String,
    period: Duration = 60.seconds,
    fn: suspend () -> T
): Timer {
    val state = GuageState()
    Gauge.builder(name) { state.value ?: 0.0 }
        .register(prometheusRegistry)

    return fixedRateTimer(
        name = "${name}_refresh",
        daemon = true,
        initialDelay = 0,
        period = period.inWholeMilliseconds
    ) {
        runCatching {
            runBlocking {
                fn().toDouble()
            }
        }.onSuccess {
            state.value = it
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