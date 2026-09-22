package no.kartverket.matrikkel.serg.formueobjekt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import no.kartverket.kotlin.mapInParallell
import no.kartverket.kotlin.retry
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.withTransaction
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import org.openapitools.client.infrastructure.ClientError
import org.openapitools.client.infrastructure.ClientException
import java.util.UUID
import javax.sql.DataSource
import no.kartverket.matrikkel.logger

class FormuesobjektSyncService(
    private val dataSource: DataSource,
    private val formueobjektApi: FormuesobjektFastEiendomApi,
    private val messageProducer : MessageProducer<Long, FastEiendomSomFormuesobjekt>
) {
    private val dokumentRepository = SergDokumentRepository(dataSource)

    suspend fun sync(antall: Int = 10): Result<Int> {
        return runCatching {
            dataSource.withTransaction { tx ->
                val dokumenter = dokumentRepository.listEtterStatus(tx, SergDokumentStatus.KREVER_SYNKRONISERING, limit = antall)

                val formueobjekter = dokumenter.mapInParallell(parallellism = 10) { dokument, index ->
                    val hendelseId = dokument.hendelse.hendelseidentifikator
                    val matrikkelenhetId = dokument.matrikkelenhetId

                    if (hendelseId == null) {
                        Pair(
                            matrikkelenhetId,
                            Result.failure(Exception("Mangler hendelseId"))
                        )
                    } else {
                        Pair(
                            matrikkelenhetId,
                            runCatching {
                                retry(
                                    attempts = 3,
                                    stopRetryIf = { exception ->
                                        when (exception) {
                                            is ClientException -> {
                                                val statusCode = exception.statusCode
                                                val body = when (val response = exception.response) {
                                                    is ClientError<*> -> response.body.toString()
                                                    else -> ""
                                                }

                                                val FFE005_generisk_feil = statusCode == 403 && body.contains("FFE-005")
                                                val FFE007_mangler_data = statusCode == 404 && body.contains("FFE-007")

                                                FFE005_generisk_feil || FFE007_mangler_data
                                            }
                                            else -> false
                                        }
                                    },
                                    fn = {
                                        formueobjektApi.hentFormuesobjektFastEiendom(
                                            rettighetspakke = "kartverketMatrikkel",
                                            hendelseidentifikator = hendelseId.toString(),
                                            korrelasjonsid = UUID.randomUUID(),
                                        )
                                    }
                                )
                            }
                        )
                    }
                }

                val pendingSends: MutableList<CompletableDeferred<Unit>> = mutableListOf()

                for ((matrikkelenhetId, formueobjekt) in formueobjekter) {
                    if (formueobjekt.isSuccess) {
                        if (formueobjekt.getOrNull() == null) {
                            logger.warn("Formuesobjekt for matrikkelenhetId: $matrikkelenhetId er null.")
                        } else {
                            pendingSends.add(messageProducer.send(ProducerRecord(
                                key = matrikkelenhetId,
                                value = formueobjekt.getOrNull()
                            )))
                        }
                    } else {
                        logger.error("Feil ved henting av formuesobjekt for matrikkelenhetId: $matrikkelenhetId", formueobjekt.exceptionOrNull())
                    }
                    dokumentRepository.settFormueobjektdata(tx, matrikkelenhetId, formueobjekt)
                }
                pendingSends.awaitAll()

                dokumenter.size // Return candidates processed
            }
        }
    }
}
