package no.kartverket.matrikkel

import io.ktor.server.application.Application
import kotlinx.coroutines.CoroutineScope
import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.matrikkel.kafka.KafkaStoredValue
import no.kartverket.matrikkel.kafka.withSelftest
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.tjenestespesifikasjoner.brreg.apis.EnheterApi
import no.kartverket.tjenestespesifikasjoner.brreg.apis.OppdateringerApi
import no.kartverket.tjenestespesifikasjoner.brreg.apis.UnderenheterApi
import kotlin.time.Duration.Companion.seconds

class Services(
    val config: Configuration,
    val offsetSync: KafkaStoredValue<Long>,
    val hendelseProducer: MessageProducer<String, Brreg.Hendelse>,
    val hendelseConsumer: MessageConsumer<String, Brreg.Hendelse>,
    val virksomhetProducer: MessageProducer<String, Brreg.Virksomhet>,
    val brregService: BrregService,
): AutoCloseable {
    context(_: CoroutineScope)
    fun waitForStartup() {
        offsetSync.start()
        offsetSync.waitToReady(30.seconds)

        offsetSync.computeIfAbsent(ENHET_OFFSET) {
            logger.info("Henter start sekvens for enheter fra {}", config.brregStartDate)
            brregService.hentStartEnhetSekvensnummerForDato(config.brregStartDate)
        }

        offsetSync.computeIfAbsent(UNDERENHET_OFFSET) {
            logger.info("Henter start sekvens for underenheter fra {}", config.brregStartDate)
            brregService.hentStartUnderenhetSekvensnummerForDato(config.brregStartDate)
        }
    }

    override fun close() {
        virksomhetProducer.close()
        hendelseProducer.close()
        hendelseConsumer.close()
        offsetSync.close()
    }
}

fun Application.createServices(config: Configuration): Services {
    val topicConfig = TopicConfiguration(config.kafka)

    val offsetSync = KafkaStoredValue(
        consumer = MessageConsumer
            .Impl(topicConfig.offsetConsumerConfig)
            .withSelftest("BRREG_OFFSET_CONSUMER", false),
        producer = MessageProducer
            .Impl(topicConfig.offsetProducerConfig)
            .withSelftest("BRREG_OFFSET_PRODUCER", false),
    )

    val hendelseProducer = MessageProducer
        .Impl(topicConfig.hendelseProducerConfig)
        .withSelftest("BRREG_HENDELSE_PRODUCER", false)

    val hendelseConsumer = MessageConsumer
        .Impl(topicConfig.hendelseConsumerConfig)
        .withSelftest("BRREG_HENDELSE_CONSUMER", false)

    val virksomhetProducer = MessageProducer
        .Impl(topicConfig.virksomhetProducerConfig)
        .withSelftest("BRREG_KAFKA_PRODUCER", false)

    val brregService = BrregService(
        enheterApi = EnheterApi(basePath = config.brregApi),
        underenheterApi = UnderenheterApi(basePath = config.brregApi),
        oppdateringerApi = OppdateringerApi(basePath = config.brregApi),
    )

    return Services(
        config = config,
        offsetSync = offsetSync,
        hendelseProducer = hendelseProducer,
        hendelseConsumer = hendelseConsumer,
        virksomhetProducer = virksomhetProducer,
        brregService = brregService
    )
}
