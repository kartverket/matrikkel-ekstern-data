package no.kartverket.matrikkel.serg.repository

import no.kartverket.matrikkel.config.Configuration
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.config.DatabaseConfiguration
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer

class SpecifiedPostgreSQLContainer : PostgreSQLContainer<SpecifiedPostgreSQLContainer>("postgres:18.3-alpine")

interface WithDatabase {
    companion object {
        private val postgreSQLContainer = SpecifiedPostgreSQLContainer().apply { start() }
        private val configuration = Configuration(
            database = DatabaseConfiguration(
                dbName = "",
                jdbcUrl = postgreSQLContainer.jdbcUrl,
                username = "",
                password = "",
            )
        )
        private val dbConfig = DataSourceConfiguration(configuration)

        @BeforeAll
        @JvmStatic
        fun setupDatabase() {
            dbConfig.runFlyway()
        }
    }
}