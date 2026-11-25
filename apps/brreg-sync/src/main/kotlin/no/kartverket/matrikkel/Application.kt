package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import no.kartverket.heimdall.common.ktor.plugins.Metrics
import no.kartverket.heimdall.common.ktor.plugins.selftest.Selftest
import no.kartverket.heimdall.common.ktor.utils.KtorServer
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("brreg-sync")

fun runApplication() {
    val config = Configuration()

    KtorServer
        .create(Netty, port = 8090) {
            install(Metrics.Plugin)
            install(Selftest.Plugin) {
                appname = "matrikkel-brreg-sync"
                version = config.version
            }

            val services = createServices(config)
            services.waitForStartup()

            startEnhetOppdateringJob(services)
            startUnderenhetOppdateringJob(services)
            startVirksomhetSyncJob(services)

            monitor.subscribe(ApplicationStopping) {
                logger.info("Stopper services")
                services.close()
            }
        }.start(wait = true)
}
