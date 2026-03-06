package no.kartverket.matrikkel.serg.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

suspend fun <A> transactional(
    dataSource: DataSource,
    operation: suspend (TransactionalSession) -> A,
): A {
    return withContext(Dispatchers.IO) {
        using(sessionOf(dataSource)) { session ->
            session.transaction {
                runBlocking(Dispatchers.IO) {
                    operation(it)
                }
            }
        }
    }
}
