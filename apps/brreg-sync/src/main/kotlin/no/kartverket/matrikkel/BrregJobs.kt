package no.kartverket.matrikkel

import io.ktor.server.application.*
import kotlinx.coroutines.*
import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Application.startEnhetOppdateringJob(services: Services): Job {
    return launchJob("henting av oppdatering for enheter") {
        val sekvensnummer = requireNotNull(services.offsetSync.getValue(ENHET_OFFSET)) {
            "Sekvenummer for enhet kan ikke være null"
        }

        val oppdateringer = services.brregService.hentOppdateringerForEnheter(sekvensnummer + 1)
        oppdateringer.map {
            services.hendelseProducer.send(
                ProducerRecord(
                    key = it.organisasjonsnr,
                    value = it
                )
            )
        }.awaitAll()

        if (oppdateringer.isNotEmpty()) {
            services.offsetSync.setValue(ENHET_OFFSET, oppdateringer.last().oppdateringsId)
        }
        logger.info("Publiserte ${oppdateringer.size} hendelser for enheter")
    }
}

fun Application.startUnderenhetOppdateringJob(services: Services): Job {
    return launchJob("henting av oppdatering for underenheter") {
        val sekvensnummer = requireNotNull(services.offsetSync.getValue(UNDERENHET_OFFSET)) {
            "Sekvenummer for underenhet kan ikke være null"
        }
        val oppdateringer = services.brregService.hentOppdateringerForUnderenheter(sekvensnummer)

        oppdateringer.map {
            services.hendelseProducer.send(
                ProducerRecord(
                    key = it.organisasjonsnr,
                    value = it
                )
            )
        }.awaitAll()

        if (oppdateringer.isNotEmpty()) {
            services.offsetSync.setValue(UNDERENHET_OFFSET, oppdateringer.last().oppdateringsId)
        }

        logger.info("Publiserte ${oppdateringer.size} hendelser for underenheter")
    }
}

fun Application.startVirksomhetSyncJob(services: Services): Job {
    return launchJob("syncing av virksomheter basert på hendelser") {
        val response = services.hendelseConsumer.poll()
        for (record in response.records) {
            val hendelse = requireNotNull(record.value)
            when (hendelse.endringstype) {
                Brreg.HendelseType.SLETTING, Brreg.HendelseType.FJERNING -> {
                    services.virksomhetProducer.send(
                        ProducerRecord(
                            key = hendelse.organisasjonsnr,
                            value = null
                        )
                    )
                }

                else -> {
                    val virksomhet = services.brregService.hentVirksomhet(hendelse)
                    services.virksomhetProducer.send(
                        ProducerRecord(
                            key = hendelse.organisasjonsnr,
                            value = virksomhet
                        )
                    )
                }
            }
        }
    }
}


private fun Application.launchJob(
    name: String,
    backoff: Duration = 10.seconds,
    action: suspend () -> Unit,
): Job {
    val job = launch(Dispatchers.IO) {
        logger.info("Starter jobb: '$name'")
        while (isActive) {
            try {
                action()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()

                if (e is CancellationException &&
                    e !is TimeoutCancellationException
                ) {
                    throw e
                }

                logger.warn("Jobb feilet: '$name'. Prøver igjen om $backoff", e)
                delay(backoff)
            }
        }
    }

    job.invokeOnCompletion {
        logger.info("Stoppet jobb: '$name'")
    }

    monitor.subscribe(ApplicationStopping) {
        logger.info("Stopper jobb: '$name'")
        runBlocking {
            job.cancelAndJoin()
        }
    }

    return job
}