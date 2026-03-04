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
import no.kartverket.matrikkel.okhttp.OkHttpUtils.AuthorizationInterceptor
import no.kartverket.matrikkel.serg.SyncHendelser
import no.kartverket.oidc.tokenclient.client.MaskinportenMachineToMachineTokenClient
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

val logger = LoggerFactory.getLogger("main")

fun runApplication() {
    val config = Configuration()
    val dataSourceConfiguration = DataSourceConfiguration(config)

    dataSourceConfiguration.runFlyway()

    KtorServer
        .create(Netty, port = 8090) {
            install(Selftest.Plugin)

            val tokenClient = MaskinportenMachineToMachineTokenClient(
                clientId = config.sergClientId,
                privateJwk = config.sergPrivateJWK,
                tokenEndpoint = config.sergTokenEndpoint,
            ).bindTo("skatteetaten:formuesobjektfasteiendom")
            val httpClient = OkHttpClient.Builder()
                .addInterceptor(
                    AuthorizationInterceptor {
                        tokenClient.createToken().serialize()
                    }
                )
                .build()
            val hendelserSync =
                launch(Dispatchers.IO) {
                    val hendelserSync =
                        SyncHendelser(
                            dataSource = dataSourceConfiguration.createDatasource(),
                            hendelserApi =
                                HendelserApi(
                                    basePath = config.sergHendelserUrl,
                                    client = httpClient
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
