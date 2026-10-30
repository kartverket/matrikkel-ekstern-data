package no.kartverket.matrikkel.database

import no.kartverket.heimdall.common.kotlin.EnvUtils.getConfigOrNull


data class DatabaseConfiguration(
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