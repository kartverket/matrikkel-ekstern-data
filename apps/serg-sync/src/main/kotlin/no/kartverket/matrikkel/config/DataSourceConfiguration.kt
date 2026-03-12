package no.kartverket.matrikkel.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

object  DataSourceConfiguration{
    fun createDatasource(
        url: String,
        credential: Credential
    ): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = credential.username
            password = credential.password
            maximumPoolSize = 10
        }
        return HikariDataSource(config)
    }

    fun runFlyway(config: DatabaseConfiguration) {
        createDatasource(
            config.jdbcUrl,
            config.adminCredential
        )
            .use {
                Flyway
                    .configure()
                    .dataSource(it)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate()
            }
    }
}
