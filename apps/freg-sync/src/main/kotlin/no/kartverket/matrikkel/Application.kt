package no.kartverket.matrikkel

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import no.kartverket.heimdall.common.ktor.plugins.Metrics
import no.kartverket.heimdall.common.ktor.plugins.selftest.Selftest
import no.kartverket.heimdall.common.ktor.utils.KtorServer
import no.kartverket.heimdall.common.tokenclient.TokenClientFactory
import no.kartverket.matrikkel.database.DataSourceConfiguration
import no.kartverket.matrikkel.database.withSelftest
import no.kartverket.matrikkel.kafka.asKafkaAuth
import no.kartverket.matrikkel.kafka.jsonSerde
import no.kartverket.matrikkel.kafka.withSelftest
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.StringSerde
import kotlin.uuid.Uuid

@Serializable
data class DummyObject(val name: String)

fun runApplication() {
    val config = Configuration()
    DataSourceConfiguration.migrate(config.database)

    val producerConfig = MessageProducer.Config(
        config.kafkaBrokerUrl,
        authentication = TokenClientFactory.MachineToMachine
            .azureAd()
            .asKafkaAuth(config.kafkaBrokerScope),
        topic = "FREG_OPPDATERING",
        keySerializer = StringSerde,
        valueSerializer = jsonSerde<DummyObject>(),
        correlationIdProvider = { Uuid.random().toHexDashString() }
    )

    KtorServer
        .create(Netty, port = 8090) {
            install(Metrics.Plugin)
            install(Selftest.Plugin) {
                appname = "matrikkel-freg-sync"
                version = config.version
            }

            val producer = MessageProducer
                .Impl(producerConfig)
                .withSelftest("FREG_KAFKA_PRODUCER", false)

            val db = DataSourceConfiguration
                .createDatasource(
                    config.database.jdbcUrl,
                    config.database.userCredential
                )
                .withSelftest("DATABASE", false)

        }.start(wait = true)
}