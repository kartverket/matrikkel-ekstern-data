package no.kartverket.matrikkel.config

import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfig
import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfigOrNull
import no.kartverket.heimdall.common.tokenclient.client.DownstreamApi

class DatabaseConfiguration(
    val env: MigrationEnv,
    val jdbcUrl: String,
    val userCredential: Credential,
    val adminCredential: Credential,
)

enum class MigrationEnv(
    val location: Array<String>
) {
    LOCAL(arrayOf("db/migration", "db/migration-test")),
    PROD(arrayOf("db/migration", "db/migration-prod"))
}

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

class Credential(
    val username: String,
    val password: String,
) {
    companion object {
        fun from(name: String) = Credential(
            username = firstNonNullOf("${name}_USERNAME", "${name}_USER"),
            password = firstNonNullOf("${name}_PASSWORD"),
        )
    }
}

private fun firstNonNullOf(vararg name: String): String {
    return name.firstNotNullOf { getConfigOrNull(it) }
}