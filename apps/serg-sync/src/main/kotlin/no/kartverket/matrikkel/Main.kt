package no.kartverket.matrikkel

import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration


fun main() {
    val config = Configuration()

    DataSourceConfiguration(config).runFlyway()

}
