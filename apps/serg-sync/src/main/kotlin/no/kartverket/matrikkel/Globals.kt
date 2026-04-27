package no.kartverket.matrikkel

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("main")
val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)