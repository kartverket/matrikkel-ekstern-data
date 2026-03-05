package no.kartverket.matrikkel

import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.okhttp.OkHttpUtils.AuthorizationInterceptor
import no.kartverket.matrikkel.serg.formueobjekt.FormueobjektSyncJob
import no.kartverket.matrikkel.serg.formueobjekt.FormuesobjektSyncService
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncJob
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncService
import no.kartverket.oidc.tokenclient.client.MaskinportenMachineToMachineTokenClient
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class Services(val config: Configuration) {
    val tokenClient = MaskinportenMachineToMachineTokenClient(
        clientId = config.sergClientId,
        privateJwk = config.sergPrivateJWK,
        tokenEndpoint = config.sergTokenEndpoint,
    )

    private val dataSource = DataSourceConfiguration(config).createDatasource()
    private val sergHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            AuthorizationInterceptor {
                tokenClient.createMachineToMachineToken("skatteetaten:formuesobjektfasteiendom").serialize()
            }
        )
        .build()

    val hendelserApi = HendelserApi(
        basePath = config.sergHendelserUrl,
        client = sergHttpClient
    )

    val hendelserSyncService = HendelserSyncService(
        dataSource = dataSource,
        hendelserApi = hendelserApi,
    )

    val hendelserSyncJob = HendelserSyncJob(
        syncService = hendelserSyncService,
        config = HendelserSyncJob.Config(
            antall = 1000,
            interval = 60.seconds
        )
    )


    val formueobjektApi = FormuesobjektFastEiendomApi(
        basePath = config.sergFormueobjektUrl,
        client = sergHttpClient
    )

    val formueobjektSyncService = FormuesobjektSyncService(
        dataSource = dataSource,
        formueobjektApi = formueobjektApi,
    )
    val formueobjektSyncJob = FormueobjektSyncJob(
        syncService = formueobjektSyncService,
        config = FormueobjektSyncJob.Config(
            antall = 10,
            interval = 60.seconds
        )
    )
}