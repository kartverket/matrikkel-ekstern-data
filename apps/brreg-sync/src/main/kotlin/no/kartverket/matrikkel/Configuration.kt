package no.kartverket.matrikkel

import no.kartverket.heimdall.common.kotlin.EnvUtils
import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfig
import no.kartverket.matrikkel.kafka.KafkaConfiguration

data class Configuration(
    val kafka: KafkaConfiguration = KafkaConfiguration(),
    val brregApi: String = getConfig("BRREG_URL"),
    val brregStartDate: String = getConfig("BRREG_START_DATE"),
    val version: String = EnvUtils.getConfigOrNull("VERSION") ?: "N/A"
)