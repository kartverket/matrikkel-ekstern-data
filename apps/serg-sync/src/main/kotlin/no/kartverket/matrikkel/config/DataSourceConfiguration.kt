package no.kartverket.matrikkel.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class DataSourceConfiguration(
    val config: Configuration,
) {
    fun createDatasource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = config.database.jdbcUrl
            username = config.database.username
            password = config.database.password
            maximumPoolSize = 10
        }
        return HikariDataSource(config)
    }

    fun runFlyway() {
        createDatasource().use {
            Flyway
                .configure()
                .dataSource(it)
                .load()
                .migrate()
        }
    }
}
