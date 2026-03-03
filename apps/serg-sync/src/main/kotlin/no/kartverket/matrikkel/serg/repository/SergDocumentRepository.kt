package no.kartverket.matrikkel.serg.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.utgdev.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

data class SergDocument(
    val matrikkelenhetId: Long,
    val hendelse: Hendelse,
    val formueobjekt: FastEiendomSomFormuesobjekt?,
    val status: SergDocumentStatus,
    val kommentar: String?,
    val sistOppdatert: LocalDateTime?,
)

enum class SergDocumentStatus {
    REQUIRE_SYNCHRONIZATION, SYNCHRONIZED, FAILURE, DELETED
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

class SergDocumentRepository(
    private val dataSource: DataSource
) {
    private val format = Json {
        isLenient = true
        serializersModule = SerializersModule {
            contextual(UUID::class, UUIDSerializer)
        }
    }

    @Language("TEXT")
    private val table: String = "serg_document"

    suspend fun getData(matrikkelenhetId: Long): SergDocument? = transactional(dataSource) { tx ->
        getData(tx, matrikkelenhetId)
    }

    fun getData(tx: Session, matrikkelenhetId: Long): SergDocument? {
        @Language("SQL")
        val sql = """
            SELECT * from $table where matrikkelenhetId = :id
        """.trimIndent()

        return tx.run(
            queryOf(
                sql,
                mapOf("id" to matrikkelenhetId)
            )
                .map(::mapSergDocument)
                .asSingle
        )
    }

    suspend fun listByStatus(status: SergDocumentStatus, limit: Int = 100): List<SergDocument> =
        transactional(dataSource) { tx ->
            listByStatus(tx, status, limit)
        }

    fun listByStatus(tx: Session, status: SergDocumentStatus, limit: Int = 100): List<SergDocument> {
        @Language("SQL")
        val sql = """
            SELECT * from $table
            WHERE status = :status
            ORDER BY sistOppdatert ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """.trimIndent()

        return tx.run(
            queryOf(
                sql, mapOf(
                    "status" to status.name,
                    "limit" to limit
                )
            )
                .map(::mapSergDocument)
                .asList
        )
    }

    suspend fun upsertFraHendelse(hendelse: Hendelse) {
        return transactional(dataSource) { tx -> upsertFraHendelse(tx, hendelse) }
    }

    fun upsertFraHendelse(
        tx: Session,
        hendelse: Hendelse,
    ) {
        val matrikkelenhetId = checkNotNull(hendelse.matrikkelUnikIdentifikator) {
            "matrikkelenhetId is required"
        }

        val status = when (hendelse.hendelsestype) {
            Hendelsestype.ny -> SergDocumentStatus.REQUIRE_SYNCHRONIZATION
            Hendelsestype.endret -> SergDocumentStatus.REQUIRE_SYNCHRONIZATION
            Hendelsestype.slettet -> SergDocumentStatus.DELETED
            null -> error("Cannot process Hendelse without a type")
        }

        @Language("SQL")
        val sql = """
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
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "hendelse" to format.encodeToString(hendelse),
                    "status" to status.name
                )
            ).asUpdate
        )
    }

    suspend fun settFormueobjektdata(matrikkelenhetId: Long, formueobjekt: Result<FastEiendomSomFormuesobjekt>) {
        return transactional(dataSource) { tx -> settFormueobjektdata(tx, matrikkelenhetId, formueobjekt) }
    }

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
                val sql = """
                UPDATE $table
                SET formueobjekt = :formueobjekt::jsonb, sistOppdatert = now(), status = :status, kommentar = NULL
                WHERE matrikkelenhetId = :id
            """.trimIndent()

                tx.run(
                    queryOf(
                        sql, mapOf(
                            "id" to matrikkelenhetId,
                            "formueobjekt" to format.encodeToString(formueobjekt),
                            "status" to SergDocumentStatus.SYNCHRONIZED.name
                        )
                    ).asUpdate
                )
            }
        )
    }

    fun settStatus(
        tx: Session,
        matrikkelenhetId: Long,
        status: SergDocumentStatus,
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
                    "status" to status.name
                )
            ).asUpdate
        )
    }

    fun settKommentar(
        tx: Session,
        matrikkelenhetId: Long,
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

    fun settSomFeil(tx: Session, matrikkelenhetId: Long, kommentar: String) {
        @Language("SQL")
        val sql = """
            UPDATE $table
            SET kommentar = :kommentar, status = :status
            WHERE matrikkelenhetId = :id
        """.trimIndent()

        tx.run(
            queryOf(
                sql, mapOf(
                    "id" to matrikkelenhetId,
                    "kommentar" to kommentar,
                    "status" to SergDocumentStatus.FAILURE.name
                )
            ).asUpdate
        )
    }

    private fun mapSergDocument(row: Row): SergDocument {
        return SergDocument(
            matrikkelenhetId = row.long("matrikkelenhetId"),
            hendelse = format.decodeFromString(row.string("hendelse")),
            formueobjekt = row.stringOrNull("formueobjekt")?.let(format::decodeFromString),
            status = SergDocumentStatus.valueOf(row.string("status")),
            kommentar = row.stringOrNull("kommentar"),
            sistOppdatert = row.localDateTime("sistOppdatert").toKotlinLocalDateTime(),
        )
    }
}
