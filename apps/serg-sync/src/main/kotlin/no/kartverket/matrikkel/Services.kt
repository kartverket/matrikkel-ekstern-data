package no.kartverket.matrikkel

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotliquery.queryOf
import kotliquery.sessionOf
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.okhttp.OkHttpUtils.AuthorizationInterceptor
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
        client = sergHttpClient,
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
        client = sergHttpClient,
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

    val healthcheckTimer: Timer

    init {
        val dbReporter = SelftestGenerator.Reporter("database", critical = true)
        val sergReporter = SelftestGenerator.Reporter("serg-register", critical = true)

        healthcheckTimer = fixedRateTimer(
            name = "sjekk kritiske avhengigheter",
            daemon = true,
            initialDelay = 0,
            period = 60.seconds.inWholeMilliseconds
        ) {
            dbReporter.ping {
                sessionOf(dataSource)
                    .run(queryOf("SELECT 1").asExecute)
            }
            sergReporter.ping {
                hendelserApi.hentStart(
                    dato = "2026-01-01",
                    korrelasjonsid = UUID.randomUUID()
                )
            }
        }

        SelftestGenerator.Metadata("dagensSekvensnummer") {
            val korrelasjonsId = UUID.randomUUID()
            val dagensDato = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.format(LocalDate.Formats.ISO)
            runBlocking {
                runCatching {
                    hendelserApi.hentStart(dato = dagensDato, korrelasjonsid = korrelasjonsId)
                }.fold(
                    onSuccess = { it.sekvensnummer ?: -1 },
                    onFailure = { -99 },
                ).toString()
            }
        }

        SelftestGenerator.Metadata("Antall som krever synkronisering") {
            runBlocking {
                val antallSomKreverSunk = runCatching {
                    sergDokumentRepository.tellEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING)
                }.fold(
                    onSuccess = { it },
                    onFailure = { -99 },
                )
                antallSomKreverSunk.toString()
            }
        }
    }
}
