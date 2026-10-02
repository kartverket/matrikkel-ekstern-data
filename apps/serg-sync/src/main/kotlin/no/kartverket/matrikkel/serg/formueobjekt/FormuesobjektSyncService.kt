package no.kartverket.matrikkel.serg.formueobjekt

import no.kartverket.eksterndata.domene.Serg.FastEiendomSomFormuesObjektHendelse
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

            val (gyldigeHendelser, ugyldigeHendelser) = hendelser.records.partition {
                it.value?.hendelseidentifikator != null
            }

            ugyldigeHendelser.forEach { record ->
                logger.warn("Hendelse mangler hendelseidentifikator, kan ikke hente formueobjekt, hopper over: ${record.value}")
            }

            val formueobjekter = gyldigeHendelser.mapInParallell(parallellism = antall) { record, _ ->
                val hendelse = requireNotNull(record.value)
                val hendelseId = requireNotNull(hendelse.hendelseidentifikator)

                Pair(
                    hendelse,
                    runCatching {
                        retry(
                            attempts = 3,
                            stopRetryIf = { forventetApiFeil(it) },
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

            val ikkeForventedeFeil = formueobjekter
                .mapNotNull { it.second.exceptionOrNull() }
                .filter { !forventetApiFeil(it) }

            if (ikkeForventedeFeil.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException("Feilet ved henting av formueobjekt for ${ikkeForventedeFeil.size} hendelser med: " + ikkeForventedeFeil.getOrNull(0)?.message)
                )
            }

            val pendingSends = buildList {
                for ((hendelse, resultatFormueobjekt) in formueobjekter.filter { it.second.isSuccess }) {
                    val formueobjekt = resultatFormueobjekt.getOrThrow()
                    val matrikkelenhetId = requireNotNull(hendelse.matrikkelUnikIdentifikator)
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

    fun forventetApiFeil(exception: Throwable): Boolean = when (exception) {
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
}
