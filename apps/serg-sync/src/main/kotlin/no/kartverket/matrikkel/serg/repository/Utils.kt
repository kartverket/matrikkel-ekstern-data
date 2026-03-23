package no.kartverket.matrikkel.serg.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

suspend fun <A> DataSource.withTransaction(operation: suspend (TransactionalSession) -> A): A {
    return withContext(Dispatchers.IO) {
        using(sessionOf(this@withTransaction)) { session ->
            session.transaction {
                runBlocking(Dispatchers.IO) {
                    operation(it)
                }
            }
        }
    }
}

fun <A> DataSource.runSql(operation: (Session) -> A): A {
    return using(sessionOf(this@runSql)) { session ->
        operation(session)
    }
}