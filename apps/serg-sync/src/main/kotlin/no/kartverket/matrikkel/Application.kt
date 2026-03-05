package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.ktor.KtorServer
import no.kartverket.ktor.Selftest
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock

val logger = LoggerFactory.getLogger("main")

fun runApplication() {
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    val services = Services(config)

    SelftestGenerator.Metadata("dagensSekvensnummer") {
        val korrelasjonsId = UUID.randomUUID()
        val dagensDato = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.format(LocalDate.Formats.ISO)
        runBlocking {
            runCatching {
                services.hendelserApi.hentStart(dato = dagensDato, korrelasjonsid = korrelasjonsId)
            }.fold(
                onSuccess = { it.sekvensnummer ?: -1 },
                onFailure = { -99 }
            ).toString()
        }
    }

    SelftestGenerator.Metadata("Antall som krever synkronisering") {
        runBlocking {
            runCatching {
                services.sergDokumentRepository.tellEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING)
            }.fold(
                onSuccess = { it },
                onFailure = { -99 }
            ).toString()
        }
    }

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
