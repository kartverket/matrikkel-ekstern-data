package no.kartverket.matrikkel.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotliquery.queryOf
import kotliquery.sessionOf
import no.kartverket.heimdall.common.ktor.plugins.selftest.SelftestGenerator
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

context(scope: CoroutineScope)
fun DataSource.withSelftest(name: String, critical: Boolean): DataSource {
    return SelftestingDataSource(this, scope, name, critical)
}

class SelftestingDataSource(
    private val dataSource: DataSource,
    private val scope: CoroutineScope,
    name: String,
    critical: Boolean,
) : DataSource by dataSource {
    val reporter = SelftestGenerator.Reporter(name, critical)

    // Java default methods are not overridden with delegation
    override fun createConnectionBuilder(): ConnectionBuilder? {
        return dataSource.createConnectionBuilder()
    }

    // Java default methods are not overridden with delegation
    override fun createShardingKeyBuilder(): ShardingKeyBuilder? {
        return dataSource.createShardingKeyBuilder()
    }

    init {
        scope.launch {
            while (scope.isActive) {
                reporter.ping {
                    sessionOf(dataSource).use { session ->
                        session.run(queryOf("SELECT 1").asExecute)
                    }
                }
                delay(30.seconds)
            }
        }
    }
}