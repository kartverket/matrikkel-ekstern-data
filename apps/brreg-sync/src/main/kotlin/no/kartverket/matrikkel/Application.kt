package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.kartverket.heimdall.common.ktor.plugins.Metrics
import no.kartverket.heimdall.common.ktor.plugins.selftest.Selftest
import no.kartverket.heimdall.common.ktor.utils.KtorServer
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

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

            val worker = if (config.dryRun) null else launch {
                try {
                    services.waitForStartup()
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    logger.error("Oppstart feilet, avslutter applikasjonen", e)
                    exitProcess(1)
                }
                startEnhetOppdateringJob(services)
                startUnderenhetOppdateringJob(services)
                startVirksomhetSyncJob(services)
            }

            monitor.subscribe(ApplicationStopping) {
                logger.info("Stopper services")
                runBlocking { worker?.cancelAndJoin() }
                services.close()
            }
        }.start(wait = true)
}
