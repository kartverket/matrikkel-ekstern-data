package no.kartverket.matrikkel

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("main")
val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)