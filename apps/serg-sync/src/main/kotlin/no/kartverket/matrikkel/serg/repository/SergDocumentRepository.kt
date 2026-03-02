package no.kartverket.matrikkel.serg.repository

import kotlinx.serialization.json.Json
import kotliquery.Session
import kotliquery.queryOf
import no.utgdev.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class SergDocumentRepository(
    private val dataSource: DataSource
) {
    private val format = Json {
        isLenient = true
    }
    private val table: String = "serg_document"

    suspend fun opprettFraHendelse(
        tx: Session,
        matrikkelenhetId: String,
        hendelse: Hendelse,
    ) {
        @Language("SQL")
        val sql = """
            INSERT INTO $table (matrikkelenhetId, hendelse, sistOppdatert, status)
            VALUES (:id, :hendelse::jsonb, now(), 'PENDING')
        """.trimIndent()

        tx.run(
            queryOf(
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "hendelse" to format.encodeToString(hendelse)
                )
            ).asUpdate
        )
    }

    suspend fun settFormueobjektdata(
        tx: Session,
        matrikkelenhetId: String,
        formueobjekt: FastEiendomSomFormuesobjekt,
    ) {
        @Language("SQL")
        val sql = """
            UPDATE $table
            SET formueobjekt = :formueobjekt::jsonb, sistOppdatert = now(), status = 'FETCHED'
            WHERE matrikkelenhetId = :id
        """.trimIndent()

        tx.run(
            queryOf(
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "formueobjekt" to format.encodeToString(formueobjekt)
                )
            ).asUpdate
        )
    }

    suspend fun settStatus(
        tx: Session,
        matrikkelenhetId: String,
        status: String,
    ) {
        @Language("SQL")
        val sql = """
            UPDATE $table
            SET status = :status, sistOppdatert = now()
            WHERE matrikkelenhetId = :id
        """.trimIndent()

        tx.run(
            queryOf(
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "status" to status
                )
            ).asUpdate
        )
    }

    suspend fun settKommentar(
        tx: Session,
        matrikkelenhetId: String,
        kommentar: String
    ) {
        @Language("SQL")
        val sql = """
            UPDATE $table
            SET kommentar = :kommentar
            WHERE matrikkelenhetId = :id
        """.trimIndent()

        tx.run(
            queryOf(
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "kommentar" to kommentar
                )
            ).asUpdate
        )
    }
}