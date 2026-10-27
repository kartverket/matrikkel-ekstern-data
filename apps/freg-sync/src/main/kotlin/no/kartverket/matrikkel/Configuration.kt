package no.kartverket.matrikkel

import io.ktor.http.Url
import no.kartverket.heimdall.common.kotlin.EnvUtils
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi
import no.kartverket.matrikkel.database.Credential
import no.kartverket.matrikkel.database.DatabaseConfiguration
import no.kartverket.matrikkel.database.MigrationEnv

data class Configuration(
    val kafkaBrokerUrl: Url = Url(EnvUtils.getConfig("KAFKA_BROKER_URL")),
    val kafkaBrokerScope: DownstreamApi = DownstreamApi.parse(EnvUtils.getConfig("KAFKA_BROKER_SCOPE")),
    val database: DatabaseConfiguration = DatabaseConfiguration(
        env = MigrationEnv.PROD,
        jdbcUrl = EnvUtils.getConfig("DB_URL"),
        userCredential = Credential.from("DB_USER"),
        adminCredential = Credential.from("DB_ADMIN"),
    ),
    val version: String = EnvUtils.getConfigOrNull("VERSION") ?: "N/A"
)