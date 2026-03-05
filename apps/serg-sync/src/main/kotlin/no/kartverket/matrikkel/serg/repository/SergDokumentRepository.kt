package no.kartverket.matrikkel.serg.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.intellij.lang.annotations.Language
import org.openapitools.client.infrastructure.Serializer
import javax.sql.DataSource

data class SergDokument(
    val matrikkelenhetId: Long,
    val hendelse: Hendelse,
    val formueobjekt: FastEiendomSomFormuesobjekt?,
    val status: SergDokumentStatus,
    val kommentar: String?,
    val sistOppdatert: LocalDateTime?,
)

enum class SergDokumentStatus {
    KREVER_SYNKRONISERING,
    SYNKRONISERT,
    FEIL,
    SLETTET,
}

class SergDokumentRepository(
    private val dataSource: DataSource,
) {
    private val format = Serializer.jacksonObjectMapper

    @Language("TEXT")
    private val table: String = "serg_dokument"

    suspend fun hentData(matrikkelenhetId: Long): SergDokument? =
        transactional(dataSource) { tx ->
            hentData(tx, matrikkelenhetId)
        }

    fun hentData(
        tx: Session,
        matrikkelenhetId: Long,
    ): SergDokument? {
        @Language("SQL")
        val sql =
            """
            SELECT * from $table where matrikkelenhetId = :id
            """.trimIndent()

        return tx.run(
            queryOf(
                sql,
                mapOf("id" to matrikkelenhetId),
            ).map(::mapSergDokument)
                .asSingle,
        )
    }

    suspend fun tellEtterStatus(status: SergDokumentStatus): Int = transactional(dataSource) { tx ->
        tellEtterStatus(tx, status)
    }

    fun tellEtterStatus(tx: Session, status: SergDokumentStatus): Int {
        return tx.run(
            queryOf("SELECT COUNT(*) from $table WHERE status = :status", status.name)
                .map { it.int(1) }
                .asSingle
        )
            ?: 0
    }

    suspend fun listEtterStatus(
        status: SergDokumentStatus,
        limit: Int = 100,
    ): List<SergDokument> =
        transactional(dataSource) { tx ->
            listEtterStatus(tx, status, limit)
        }

    fun listEtterStatus(
        tx: Session,
        status: SergDokumentStatus,
        limit: Int = 100,
    ): List<SergDokument> {
        @Language("SQL")
        val sql =
            """
            SELECT * from $table
            WHERE status = :status
            ORDER BY sistOppdatert ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """.trimIndent()

        return tx.run(
            queryOf(
                sql,
                mapOf(
                    "status" to status.name,
                    "limit" to limit,
                ),
            ).map(::mapSergDokument)
                .asList,
        )
    }

    suspend fun upsertFraHendelse(hendelse: Hendelse) = transactional(dataSource) { tx -> upsertFraHendelse(tx, hendelse) }

    fun upsertFraHendelse(
        tx: Session,
        hendelse: Hendelse,
    ) {
        val matrikkelenhetId = checkNotNull(hendelse.matrikkelUnikIdentifikator) {
            "matrikkelenhetId is required"
        }

        val status = when (hendelse.hendelsestype) {
            Hendelsestype.ny -> SergDokumentStatus.KREVER_SYNKRONISERING
            Hendelsestype.endret -> SergDokumentStatus.KREVER_SYNKRONISERING
            Hendelsestype.slettet -> SergDokumentStatus.SLETTET
            null -> error("Cannot process Hendelse without a type")
        }

        @Language("SQL")
        val sql =
            """
            INSERT INTO $table (matrikkelenhetId, hendelse, sistOppdatert, status)
            VALUES (:id, :hendelse::jsonb, now(), :status)
            ON CONFLICT (matrikkelenhetId)
            DO UPDATE SET 
                hendelse = EXCLUDED.hendelse,
                sistOppdatert = now(),
                status = EXCLUDED.status
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to matrikkelenhetId,
                    "hendelse" to format.writeValueAsString(hendelse),
                    "status" to status.name,
                ),
            ).asUpdate,
        )
    }

    suspend fun settFormueobjektdata(
        matrikkelenhetId: Long,
        formueobjekt: Result<FastEiendomSomFormuesobjekt>,
    ) = transactional(dataSource) { tx -> settFormueobjektdata(tx, matrikkelenhetId, formueobjekt) }

    fun settFormueobjektdata(
        tx: Session,
        matrikkelenhetId: Long,
        result: Result<FastEiendomSomFormuesobjekt>,
    ) {
        result.fold(
            onFailure = { exception ->
                settSomFeil(tx, matrikkelenhetId, exception.message ?: "Ukjent feil")
            },
            onSuccess = { formueobjekt ->
                @Language("SQL")
                val sql =
                    """
                    UPDATE $table
                    SET formueobjekt = :formueobjekt::jsonb, sistOppdatert = now(), status = :status, kommentar = NULL
                    WHERE matrikkelenhetId = :id
                    """.trimIndent()

                tx.run(
                    queryOf(
                        sql,
                        mapOf(
                            "id" to matrikkelenhetId,
                            "formueobjekt" to format.writeValueAsString(formueobjekt),
                            "status" to SergDokumentStatus.SYNKRONISERT.name,
                        ),
                    ).asUpdate,
                )
            },
        )
    }

    fun settStatus(
        tx: Session,
        matrikkelenhetId: Long,
        status: SergDokumentStatus,
    ) {
        @Language("SQL")
        val sql =
            """
            UPDATE $table
            SET status = :status, sistOppdatert = now()
            WHERE matrikkelenhetId = :id
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to matrikkelenhetId,
                    "status" to status.name,
                ),
            ).asUpdate,
        )
    }

    fun settKommentar(
        tx: Session,
        matrikkelenhetId: Long,
        kommentar: String,
    ) {
        @Language("SQL")
        val sql =
            """
            UPDATE $table
            SET kommentar = :kommentar
            WHERE matrikkelenhetId = :id
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to matrikkelenhetId,
                    "kommentar" to kommentar,
                ),
            ).asUpdate,
        )
    }

    fun settSomFeil(
        tx: Session,
        matrikkelenhetId: Long,
        kommentar: String,
    ) {
        @Language("SQL")
        val sql =
            """
            UPDATE $table
            SET kommentar = :kommentar, status = :status
            WHERE matrikkelenhetId = :id
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to matrikkelenhetId,
                    "kommentar" to kommentar,
                    "status" to SergDokumentStatus.FEIL.name,
                ),
            ).asUpdate,
        )
    }

    private fun mapSergDokument(row: Row): SergDokument {
        return SergDokument(
            matrikkelenhetId = row.long("matrikkelenhetId"),
            hendelse = format.readValue(row.string("hendelse")),
            formueobjekt = row.stringOrNull("formueobjekt")?.let(format::readValue),
            status = SergDokumentStatus.valueOf(row.string("status")),
            kommentar = row.stringOrNull("kommentar"),
            sistOppdatert = row.localDateTime("sistOppdatert").toKotlinLocalDateTime(),
        )
    }
}
