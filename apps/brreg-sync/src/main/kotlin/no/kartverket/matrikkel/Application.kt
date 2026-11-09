package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import no.kartverket.heimdall.common.ktor.plugins.Metrics
import no.kartverket.heimdall.common.ktor.plugins.selftest.Selftest
import no.kartverket.heimdall.common.ktor.utils.KtorServer
import no.kartverket.heimdall.common.tokenclient.TokenClientFactory
import no.kartverket.matrikkel.kafka.KafkaStoredValue
import no.kartverket.matrikkel.kafka.asKafkaAuth
import no.kartverket.matrikkel.kafka.withSelftest
import no.kartverket.matrikkel.kafkaclient.LongSerde
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.StringSerde
import no.kartverket.matrikkel.kafkaclient.jsonSerde
import kotlin.uuid.Uuid

@Serializable
data class DummyObject(val name: String)

fun runApplication() {
    val config = Configuration()

    val authentication = TokenClientFactory.MachineToMachine
        .azureAd()
        .asKafkaAuth(config.kafka.brokerScope)

    val producerConfig = MessageProducer.Config(
        config.kafka.brokerUrl,
        authentication = authentication,
        topic = "BRREG_OPPDATERING",
        keySerializer = StringSerde,
        valueSerializer = jsonSerde<DummyObject>(),
        correlationIdProvider = { Uuid.random().toHexDashString() }
    )

    val offsetSync = KafkaStoredValue(
        config = config.kafka,
        topic = "BRREG_OFFSET",
        serde = LongSerde,
        authentication = authentication
    )

    KtorServer
        .create(Netty, port = 8090) {
            install(Metrics.Plugin)
            install(Selftest.Plugin) {
                appname = "matrikkel-brreg-sync"
                version = config.version
            }

            // offsetSync.start()
            // offsetSync.waitToReady()

            val producer = MessageProducer
                .Impl(producerConfig)
                .withSelftest("BRREG_KAFKA_PRODUCER", false)

        }.start(wait = true)
}