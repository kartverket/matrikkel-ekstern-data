package no.kartverket.matrikkel.config

import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfig
import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfigOrNull
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi
import no.kartverket.matrikkel.database.Credential
import no.kartverket.matrikkel.database.DatabaseConfiguration
import no.kartverket.matrikkel.database.MigrationEnv

class Configuration(
    val kafkaBrokerUrl: String = getConfig("KAFKA_BROKER_URL"),
    val kafkaBrokerScope: DownstreamApi = DownstreamApi.parse(getConfig("KAFKA_BROKER_SCOPE")),
    val sergHendelserUrl: String = getConfig("SERG_HENDELSER_URL"),
    val sergFormueobjektUrl: String = getConfig("SERG_FORMUEOBJEKT_URL"),
    val database: DatabaseConfiguration = DatabaseConfiguration(
        env = MigrationEnv.valueOf(getConfigOrNull("DB_ENV") ?: MigrationEnv.PROD.name),
        jdbcUrl = getConfig("DB_URL"),
        userCredential = Credential.from("DB_USER"),
        adminCredential = Credential.from("DB_ADMIN"),
    ),
    val runHendelseSync: Boolean = getConfigOrNull("RUN_HENDELSE_SYNC")?.toBooleanStrictOrNull() ?: false,
    val runFormueobjektSync: Boolean = getConfigOrNull("RUN_FORMUEOBJEKT_SYNC")?.toBooleanStrictOrNull() ?: false,
    val version: String = getConfigOrNull("VERSION") ?: "N/A"
)