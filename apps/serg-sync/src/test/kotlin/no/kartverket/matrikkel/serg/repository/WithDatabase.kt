package no.kartverket.matrikkel.serg.repository

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.kartverket.matrikkel.config.DataSourceConfiguration
import no.kartverket.matrikkel.config.MigrationEnv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

interface WithDatabase {
    companion object {
        private val postgres = PostgreSQLContainer("postgres:18.3-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .apply { start() }

        private val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 5
            }
        )


        @BeforeAll
        @JvmStatic
        fun setup(): Unit {
            DataSourceConfiguration.migrate(dataSource, MigrationEnv.LOCAL)
        }
    }

    @AfterEach
    fun cleanupDatabase() {
        println("Removing database data")
        dataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    truncate table serg_dokument, keyvalue, hendelse, matrikkelenhet_eiere_m22, person_identer_m22
                    restart identity
                    ;
                    INSERT INTO keyvalue(key, value) VALUES ('sekvensnummer', '1')
                    ;
                    SELECT refresh_eierdiff()
                    """.trimIndent()
                )
            }
        }
    }

    fun dataSource(): DataSource = dataSource
    fun connectionUrl(): String = postgres.jdbcUrl
}
