package no.kartverket.matrikkel.serg.formueobjekt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import no.kartverket.kotlin.retry
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.openapitools.client.infrastructure.ClientError
import org.openapitools.client.infrastructure.ClientException
import java.util.UUID
import no.kartverket.matrikkel.logger

class FormuesobjektSyncService(
    private val formueobjektApi: FormuesobjektFastEiendomApi,
    private val messageConsumer: MessageConsumer<Long, Hendelse>,
    private val messageProducer : MessageProducer<Long, FastEiendomSomFormuesobjekt>
) {

    suspend fun sync(antall: Int = 10): Result<Int> {

        return runCatching {

            val hendelser = messageConsumer.poll(maxRecords = antall)

            val formueobjekter: MutableList<Pair<Long?, Result<FastEiendomSomFormuesobjekt>>> = mutableListOf()

            for (record in hendelser.records) {
                val matrikkelenhetId = record.key
                val hendelseId = record.value?.hendelseidentifikator
                val resultat = Pair(
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
                formueobjekter.add(resultat)
            }

            val pendingSends: MutableList<CompletableDeferred<Unit>> = mutableListOf()

            for ((matrikkelenhetId, formueobjekt) in formueobjekter) {
                if (formueobjekt.isSuccess) {
                    if (formueobjekt.getOrNull() == null) {
                        logger.warn("Formuesobjekt for matrikkelenhetId: $matrikkelenhetId er null.")
                    }
                    pendingSends.add(messageProducer.send(ProducerRecord(
                        key = matrikkelenhetId!!,
                        value = formueobjekt.getOrThrow()
                    )))
                } else {
                    logger.error("Feilet med henting av formuesobjekt for matrikkelenhetId: $matrikkelenhetId", formueobjekt.exceptionOrNull())
                }
            }

            pendingSends.awaitAll()

            messageConsumer.commitSync()

            pendingSends.size
        }
    }
}
