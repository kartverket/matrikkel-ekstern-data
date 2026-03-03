package no.kartverket.matrikkel.config


class DatabaseConfiguration(
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

class Configuration(
    val sergBaseUrl: String = getRequiredConfig("SERG_BASE_URL"),
    val sergPrivateJWK: String = getRequiredConfig("SERG_PRIVATE_JWK"),
    val database: DatabaseConfiguration = DatabaseConfiguration(
        jdbcUrl     = getRequiredConfig("DB_URL"),
        username = getRequiredConfig("DB_USERNAME"),
        password = getRequiredConfig("DB_PASSWORD"),
    )
)

private fun getConfig(name: String): String? {
    return System.getProperty(name, System.getenv(name))
}
private fun getRequiredConfig(name: String): String {
    return requireNotNull(getConfig(name)) {
        "$name must be defined in java properties or environment"
    }
}