package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.serg.SyncHendelser
import no.utgdev.kotlin.SelftestGenerator
import no.utgdev.ktor.KtorServer
import no.utgdev.ktor.Selftest
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds


val logger = LoggerFactory.getLogger("main")
fun main() {
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    KtorServer.create(Netty, port = 8080) {
        install(Selftest.Plugin)

        val hendelserSync = launch(Dispatchers.IO) {
            val hendelserSync = SyncHendelser(
                dataSource = dataSourceConfiguration.createDatasource(),
                hendelserApi = HendelserApi(
                    basePath = config.sergBaseUrl
                )
            )

            do {
                val hendelser = hendelserSync.sync(1000)
                val antallHentet = hendelser
                    .map { it.size }
                    .fold(
                        onFailure = {
                            logger.error("Feilet med henting av hendelser", it)
                            0
                        },
                        onSuccess = {
                            logger.info("Hentet $it hendelser fra SERG")
                            it
                        },
                    )

                if (antallHentet != 1000) {
                    delay(60.seconds)
                }
            } while (true)
        }
    }.start(wait = true)
}
