package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.kartverket.ktor.KtorServer
import no.kartverket.ktor.Selftest
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.serg.SyncHendelser
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import org.slf4j.LoggerFactory
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.reader
import kotlin.time.Duration.Companion.seconds

class Env {
    companion object {
        fun load(file: String) {
            val env = Properties()
            env.load(Path(file).reader())

            env.entries.forEach { entry ->
                System.setProperty(entry.key.toString(), entry.value.toString())
            }
        }
    }
}

val logger = LoggerFactory.getLogger("main")

fun main() {
    Env.load("docker/idea.env")
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    KtorServer
        .create(Netty, port = 8090) {
            install(Selftest.Plugin)

            val hendelserSync =
                launch(Dispatchers.IO) {
                    val hendelserSync =
                        SyncHendelser(
                            dataSource = dataSourceConfiguration.createDatasource(),
                            hendelserApi =
                                HendelserApi(
                                    basePath = config.sergBaseUrl,
                                ),
                        )

                    do {
                        logger.info("Henter ut hendelser")
                        val hendelser = hendelserSync.sync(1000)
                        val antallHentet =
                            hendelser
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
                            delay(5.seconds)
                        }
                    } while (true)
                }
        }.start(wait = true)
}
