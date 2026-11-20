@file:OptIn(ExperimentalCoroutinesApi::class)
package no.kartverket.matrikkel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfig
import no.kartverket.heimdall.common.tokenclient.TokenClientFactory
import no.kartverket.matrikkel.kafka.KafkaConfiguration
import no.kartverket.matrikkel.kafka.asKafkaAuth
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.matrikkel.kafkaclient.StringSerde
import no.kartverket.matrikkel.kafkaclient.jsonSerde
import no.kartverket.tjenestespesifikasjoner.brreg.apis.EnheterApi
import no.kartverket.tjenestespesifikasjoner.brreg.apis.UnderenheterApi
import no.kartverket.tjenestespesifikasjoner.brreg.models.*
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

private data class Configuration(
    val kafka: KafkaConfiguration = KafkaConfiguration(),
    val brregApi: String = getConfig("BRREG_URL"),
    val dryRun: Boolean = getConfig("DRY_RUN").toBooleanStrict(),
)

private val logger = LoggerFactory.getLogger("brreg-datalast-job")
private const val topic = "BRREG_VIRKSOMHET"
private const val bufferSize = 100

fun runBrregDatalastJob(): Unit = runBlocking {
    logger.info("Starter datalast")
    val config = Configuration()
    val producer = MessageProducer.Impl(
        MessageProducer.Config(
            server = config.kafka.brokerUrl,
            authentication = TokenClientFactory.MachineToMachine
                .azureAd()
                .asKafkaAuth(config.kafka.brokerScope),
            topic = topic,
            keySerializer = StringSerde,
            valueSerializer = jsonSerde<Brreg.Virksomhet>(),
            correlationIdProvider = { Uuid.random().toHexDashString() },
            bufferSize = bufferSize,
        )
    )

    if (!config.dryRun) {
        check(producer.metadata().canPublish) { "Could not publish to topic $topic" }
        logger.info("Kontakt med kafka, og verifisert publiseringsrettigheter")
    }


    EnheterApi(basePath = config.brregApi, client = brregClient).lastnedEnheter()
        .inputStream()
        .unzip()
        .parseJson<Enhet>()
        .map { it.toKafkaDTO() }
        .run {
            if (config.dryRun) {
                logger.info("Leste inn ${count()} enheter")
            } else {
                publishTo(producer, logtype = "enheter")
            }
        }

    UnderenheterApi(basePath = config.brregApi, client = brregClient).lastnedUnderenheter()
        .inputStream()
        .unzip()
        .parseJson<Underenhet>()
        .map { it.toKafkaDTO() }
        .run {
            if (config.dryRun) {
                logger.info("Leste inn ${count()} underenheter")
            } else {
                publishTo(producer, logtype = "underenheter")
            }
        }

    producer.close()
}

private suspend fun Flow<Brreg.Virksomhet>.publishTo(producer: MessageProducer<String, Brreg.Virksomhet>, logtype: String = "ukjent type") {
    var counter = 0
    var lastLogCounter = 0
    logger.info("Starter publisering av $logtype")
    this
        .chunked(bufferSize)
        .collect { virksomheter ->
            virksomheter.map {
                producer.send(
                    ProducerRecord(
                        key = it.organisasjonsnr,
                        value = it
                    )
                )
            }.awaitAll()
            counter += virksomheter.size
            if (counter - lastLogCounter >= 1_000) {
                logger.info("Publisert $counter av $logtype")
                lastLogCounter = counter
            }
        }
    logger.info("Publisering av $logtype fullført ($counter)")
}

private fun Enhet.toKafkaDTO(): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.ENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = this.postadresse?.toKafkaDTO(),
        forretningsadresse = this.forretningsadresse?.toKafkaDTO(),
        tlf = this.telefon ?: this.mobil,
        slettetDato = null,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

private fun Underenhet.toKafkaDTO(): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.UNDERENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = this.postadresse?.toKafkaDTO(),
        forretningsadresse = null,
        tlf = this.telefon ?: this.mobil,
        slettetDato = null,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

// Adresse-typene fra OAS er forskjellige (men like) objekter. Trenger derfor separate mappere
private fun EnhetPostadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}

private fun UnderenhetPostadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}

private fun EnhetForretningsadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}