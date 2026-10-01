package no.kartverket.matrikkel.serg.formueobjekt

import FastEiendomSomFormuesObjektHendelse
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import no.kartverket.kotlin.mapInParallell
import no.kartverket.kotlin.retry
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.matrikkel.logger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.openapitools.client.infrastructure.ClientError
import org.openapitools.client.infrastructure.ClientException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class FormuesobjektSyncService(
    private val formueobjektApi: FormuesobjektFastEiendomApi,
    private val messageConsumer: MessageConsumer<Long, Hendelse>,
    private val messageProducer : MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>
) {
    private val fastEiendomSomFormuesObjektHendelseMapper = FastEiendomSomFormuesObjektHendelseMapper()

    suspend fun sync(antall: Int = 10): Result<Int> {
        return runCatching {

            val hendelser = messageConsumer.poll(maxRecords = antall)

            val (gyldigeRecords, ugyldigeRecords) = hendelser.records.partition {
                it.value?.hendelseidentifikator != null
            }

            ugyldigeRecords.forEach { record ->
                logger.warn("Hendelse mangler hendelseidentifikator, kan ikke hente formueobjekt, hopper over: ${record.value}")
            }

            val formueobjekter = gyldigeRecords.mapInParallell(parallellism = antall) { record, _ ->
                val hendelse = requireNotNull(record.value)
                val hendelseId = hendelse.hendelseidentifikator

                if (hendelseId  == null) {
                    throw IllegalStateException("Hendelse mangler hendelseidentifikator: $hendelse")
                }

                Pair(
                    hendelse,
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

            val feilet = formueobjekter.stream().filter { it.second.isFailure }.toList()

            if (feilet.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException("Feilet med henting av formueobjekt for ${feilet.size} hendelser med: " + feilet.getOrNull(0)?.second?.exceptionOrNull()?.message)
                )
            }

            val pendingSends = buildList {
                for ((hendelse, resultatFormueobjekt) in formueobjekter) {
                    val formueobjekt = resultatFormueobjekt.getOrThrow()
                    val matrikkelenhetId = hendelse?.matrikkelUnikIdentifikator!!
                    val fastEiendomSomFormuesObjektHendelse = fastEiendomSomFormuesObjektHendelseMapper.map(hendelse, formueobjekt)
                    add(
                        messageProducer.send(ProducerRecord(
                            key = matrikkelenhetId,
                            value = fastEiendomSomFormuesObjektHendelse
                        ))
                    )
                }
            }

            withTimeout(10.seconds) {
                pendingSends.awaitAll()
            }

            messageConsumer.commitSync()

            pendingSends.size
        }
    }


}
