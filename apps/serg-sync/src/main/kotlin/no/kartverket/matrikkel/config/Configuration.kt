package no.kartverket.matrikkel.config

class DatabaseConfiguration(
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

class Configuration(
    val sergHendelserUrl: String = getRequiredConfig("SERG_HENDELSER_URL"),
    val sergFormueobjektUrl: String = getRequiredConfig("SERG_FORMUEOBJEKT_URL"),
    val sergClientId: String = getRequiredConfig("SERG_CLIENT_ID"),
    val sergPrivateJWK: String = getRequiredConfig("SERG_PRIVATE_JWK"),
    val sergTokenEndpoint: String = getRequiredConfig("SERG_TOKEN_ENDPOINT"),
    val database: DatabaseConfiguration = DatabaseConfiguration(
        jdbcUrl = getRequiredConfig("POSTGRES_URL"),
        username = getRequiredConfig("POSTGRES_USER"),
        password = getRequiredConfig("POSTGRES_PASSWORD"),
    ),
    val runHendelseSync: Boolean = getRequiredConfig("RUN_HENDELSE_SYNC").toBooleanStrictOrNull() ?: false,
    val runFormueobjektSync: Boolean = getRequiredConfig("RUN_FORMUEOBJEKT_SYNC").toBooleanStrictOrNull() ?: false,
)

private fun getConfig(name: String): String? {
    return System.getProperty(name, System.getenv(name))
}

private fun getRequiredConfig(name: String): String {
    return requireNotNull(getConfig(name)) {
        "$name must be defined in java properties or environment"
    }
}
