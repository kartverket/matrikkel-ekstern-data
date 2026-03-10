package no.kartverket.matrikkel

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.kotlin.cache
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.okhttp.OkHttpUtils.AuthorizationInterceptor
import no.kartverket.matrikkel.okhttp.OkHttpUtils.MetricsInterceptor
import no.kartverket.matrikkel.okhttp.OkHttpUtils.addInterceptorAtStart
import no.kartverket.matrikkel.serg.formueobjekt.FormueobjektSyncJob
import no.kartverket.matrikkel.serg.formueobjekt.FormuesobjektSyncService
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncJob
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncService
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.oidc.tokenclient.client.MaskinportenMachineToMachineTokenClient
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import okhttp3.OkHttpClient
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class Services(
    val config: Configuration,
) {
    val tokenClient = MaskinportenMachineToMachineTokenClient(
        clientId = config.sergClientId,
        privateJwk = config.sergPrivateJWK,
        tokenEndpoint = config.sergTokenEndpoint,
    )

    val dataSource = DataSourceConfiguration(config).createDatasource()
    val keyValueRepository = KeyValueRepository(dataSource)
    val sergDokumentRepository = SergDokumentRepository(dataSource)

    private val sergHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            AuthorizationInterceptor {
                tokenClient.createMachineToMachineToken("skatteetaten:formuesobjektfasteiendom").serialize()
            },
        )
        .build()

    val hendelserApi = HendelserApi(
        basePath = config.sergHendelserUrl,
        client = sergHttpClient.newBuilder()
            .addInterceptorAtStart(MetricsInterceptor("HendelserApi", prometheusRegistry))
            .build(),
    )

    val hendelserSyncService = HendelserSyncService(
        dataSource = dataSource,
        hendelserApi = hendelserApi,
    )

    val hendelserSyncJob = HendelserSyncJob(
        syncService = hendelserSyncService,
        config = HendelserSyncJob.Config(
            antall = 1000,
            interval = 60.seconds,
        ),
    )

    val formueobjektApi = FormuesobjektFastEiendomApi(
        basePath = config.sergFormueobjektUrl,
        client = sergHttpClient.newBuilder()
            .addInterceptorAtStart(MetricsInterceptor("FormuesobjektFastEiendomApi", prometheusRegistry))
            .build(),
    )

    val formueobjektSyncService = FormuesobjektSyncService(
        dataSource = dataSource,
        formueobjektApi = formueobjektApi,
    )
    val formueobjektSyncJob = FormueobjektSyncJob(
        syncService = formueobjektSyncService,
        config = FormueobjektSyncJob.Config(
            antall = 10,
            interval = 60.seconds,
        ),
    )

    val timers = mutableListOf<Timer>()

    private data class HendelserStatus(
        val startIDag: Long,
        val naverende: Long,
    ) {
        fun lag(): Long = startIDag - naverende
    }

    private suspend fun kalkulerHendelserStatus(): HendelserStatus {
        val dagensDato = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.format(LocalDate.Formats.ISO)
        val startIDag = hendelserApi.hentStart(
            dato = dagensDato,
            korrelasjonsid = UUID.randomUUID()
        ).sekvensnummer ?: 0

        val naverendeSekvensnummer = keyValueRepository.getValue("sekvensnummer").toLong()

        return HendelserStatus(startIDag, naverendeSekvensnummer)
    }

    private suspend fun antallJobberMedStatus(status: SergDokumentStatus): Long {
        return runCatching {
            sergDokumentRepository.tellEtterStatus(status)
        }.fold(
            onSuccess = { it },
            onFailure = { -99 },
        )
    }

    init {
        val dbReporter = SelftestGenerator.Reporter("database", critical = true)
        val sergReporter = SelftestGenerator.Reporter("serg-register", critical = true)
        val hendelserStatus: HendelserStatus by cache(ttl = 10.seconds.toJavaDuration()) {
            runBlocking {
                kalkulerHendelserStatus()
            }
        }

        timers += refreshingGauge("sync_hendelser_lag", 0) {
            hendelserStatus.lag()
        }
        timers += refreshingGauge("sync_hendeler_pending", 0) {
            antallJobberMedStatus(SergDokumentStatus.KREVER_SYNKRONISERING)
        }

        timers += fixedRateTimer(
            name = "sjekk kritiske avhengigheter",
            daemon = true,
            initialDelay = 0,
            period = 60.seconds.inWholeMilliseconds
        ) {
            dbReporter.ping {
                using(sessionOf(dataSource)) { session ->
                    session.run(queryOf("SELECT 1").asExecute)
                }
            }

            sergReporter.ping {
                hendelserApi.hentStart(
                    dato = "2026-01-01",
                    korrelasjonsid = UUID.randomUUID()
                )
            }
        }

        SelftestGenerator.Metadata("Nåværende sekvensnummer") {
            runBlocking {
                hendelserStatus.naverende.toString()
            }
        }

        SelftestGenerator.Metadata("Start sekvensnummer i dag") {
            runBlocking {
                hendelserStatus.startIDag.toString()
            }
        }

        SelftestGenerator.Metadata("Sync hendeler lagg") {
            runBlocking {
                hendelserStatus.lag().toString()
            }
        }

        SelftestGenerator.Metadata("Antall som krever synkronisering") {
            runBlocking {
                antallJobberMedStatus(SergDokumentStatus.KREVER_SYNKRONISERING).toString()
            }
        }

        SelftestGenerator.Metadata("Antall som har feilet") {
            runBlocking {
                antallJobberMedStatus(SergDokumentStatus.FEIL).toString()
            }
        }
    }
}
