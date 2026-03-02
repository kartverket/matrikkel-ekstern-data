package no.kartverket.matrikkel.config


class DatabaseConfiguration(
    val dbName: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

class Configuration(
    val database: DatabaseConfiguration = DatabaseConfiguration(
        dbName = getRequiredConfig("DB_NAME"),
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