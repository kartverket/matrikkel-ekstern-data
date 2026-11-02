package no.kartverket.matrikkel.kafka

import io.ktor.http.Url
import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfig
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi

data class KafkaConfiguration(
    val brokerUrl: Url = Url(getConfig("KAFKA_BROKER_URL")),
    val brokerScope: DownstreamApi = DownstreamApi.parse(getConfig("KAFKA_BROKER_SCOPE")),
)