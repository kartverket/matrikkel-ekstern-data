package no.kartverket.matrikkel

import no.kartverket.heimdall.common.kotlin.EnvUtils
import no.kartverket.matrikkel.kafka.KafkaConfiguration

data class Configuration(
    val kafka: KafkaConfiguration = KafkaConfiguration(),
    val version: String = EnvUtils.getConfigOrNull("VERSION") ?: "N/A"
)