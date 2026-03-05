package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.kartverket.ktor.KtorServer
import no.kartverket.ktor.Selftest
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("main")

fun runApplication() {
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    val services = Services(config)

    KtorServer
        .create(Netty, port = 8090) {
            install(Selftest.Plugin) {
                appname = "matrikkel-serg-sync"
            }

            val hendelserSync = launch(Dispatchers.IO) {
                services.hendelserSyncJob.start()
            }

            /*
            val formueobjektSync = launch(Dispatchers.IO) {
                services.formueobjektSyncJob.start()
            }
             */

            monitor.subscribe(ApplicationStopping) {
                hendelserSync.cancel()
                // formueobjektSync.cancel()
            }
        }.start(wait = true)
}
