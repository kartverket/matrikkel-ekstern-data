package no.kartverket.matrikkel.config

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DataSourceConfiguration(val config: Configuration) {

    fun createDatasource(): DataSource {
        return TODO()
    }

    fun runFlyway() {
        Flyway
            .configure()
            .dataSource(createDatasource())
            .load()
            .migrate()
    }
}