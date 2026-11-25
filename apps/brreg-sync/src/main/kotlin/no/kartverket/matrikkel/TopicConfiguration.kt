package no.kartverket.matrikkel

import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.heimdall.common.tokenclient.TokenClientFactory
import no.kartverket.matrikkel.kafka.KafkaConfiguration
import no.kartverket.matrikkel.kafka.asKafkaAuth
import no.kartverket.matrikkel.kafkaclient.InitialOffsetPolicy
import no.kartverket.matrikkel.kafkaclient.LongSerde
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.StringSerde
import no.kartverket.matrikkel.kafkaclient.jsonSerde
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class TopicConfiguration(config: KafkaConfiguration) {
    private val HENDELSE_TOPIC = "BRREG_HENDELSE"
    private val VIRKSOMHET_TOPIC = "BRREG_VIRKSOMHET"
    private val OFFSET_TOPIC = "BRREG_OFFSET"

    private val authentication = TokenClientFactory.MachineToMachine
        .azureAd()
        .asKafkaAuth(config.brokerScope)

    val offsetConsumerConfig = MessageConsumer.Config(
        server = config.brokerUrl,
        authentication = authentication,
        topic = OFFSET_TOPIC,
        keySerializer = StringSerde,
        valueSerializer = LongSerde,
        correlationIdProvider = { Uuid.random().toHexDashString() },
        consumerGroup = OFFSET_TOPIC,
        instanceId = Uuid.random().toHexDashString(),
        initialOffsetPolicy = InitialOffsetPolicy.EARLIEST,
    )

    val offsetProducerConfig = MessageProducer.Config(
        server = config.brokerUrl,
        authentication = authentication,
        topic = OFFSET_TOPIC,
        keySerializer = StringSerde,
        valueSerializer = LongSerde,
        correlationIdProvider = { Uuid.random().toHexDashString() },
        bufferSize = 1,
        linger = 10.milliseconds,
    )

    val hendelseProducerConfig = MessageProducer.Config(
        config.brokerUrl,
        authentication = authentication,
        topic = HENDELSE_TOPIC,
        keySerializer = StringSerde,
        valueSerializer = jsonSerde<Brreg.Hendelse>(),
        correlationIdProvider = Uuid.random()::toHexDashString
    )

    val hendelseConsumerConfig = MessageConsumer.Config(
        config.brokerUrl,
        authentication = authentication,
        topic = HENDELSE_TOPIC,
        keySerializer = StringSerde,
        valueSerializer = jsonSerde<Brreg.Hendelse>(),
        correlationIdProvider = Uuid.random()::toHexDashString,
        consumerGroup = "brreg-sync",
        instanceId = Uuid.random().toHexDashString(),
        initialOffsetPolicy = InitialOffsetPolicy.EARLIEST,
    )

    val virksomhetProducerConfig = MessageProducer.Config(
        config.brokerUrl,
        authentication = authentication,
        topic = VIRKSOMHET_TOPIC,
        keySerializer = StringSerde,
        valueSerializer = jsonSerde<Brreg.Virksomhet>(),
        correlationIdProvider = Uuid.random()::toHexDashString
    )
}