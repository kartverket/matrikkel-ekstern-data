package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.kartverket.ktor.KtorServer
import no.kartverket.ktor.Metrics
import no.kartverket.ktor.Selftest
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import kotlin.reflect.KSuspendFunction0

typealias SyncJob = KSuspendFunction0<Unit>

fun runApplication() {
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    val services = Services(config)

    KtorServer
        .create(Netty, port = 8090) {
            install(Metrics.Plugin) {
                registry = prometheusRegistry
            }
            install(Selftest.Plugin) {
                appname = "matrikkel-serg-sync"
            }

            val syncJobs = listOf<SyncJob>(
                services.hendelserSyncJob::start,
                // services.formueobjektSyncJob::start,
            ).map { job ->
                launch(Dispatchers.IO) {
                    job.invoke()
                }
            }

            monitor.subscribe(ApplicationStopping) {
                services.healthcheckTimer.cancel()
                syncJobs.forEach {
                    it.cancel(message = "Application is shutting down")
                }
            }
        }.start(wait = true)
}
