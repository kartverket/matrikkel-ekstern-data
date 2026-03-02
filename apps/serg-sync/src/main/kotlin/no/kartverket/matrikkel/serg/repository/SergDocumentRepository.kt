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
    REQUIRE_SYNCHRONIZATION, SYNCHRONIZED, FAILURE,
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

    suspend fun upsertFraHendelse(
        hendelse: Hendelse,
    ) = transactional(dataSource) { tx -> upsertFraHendelse(tx, hendelse) }

    fun upsertFraHendelse(
        tx: Session,
        hendelse: Hendelse,
    ) {
        val matrikkelenhetId = requireNotNull(hendelse.matrikkelUnikIdentifikator) {
            "matrikkelenhetId is required"
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
                    "status" to SergDocumentStatus.REQUIRE_SYNCHRONIZATION.name
                )
            ).asUpdate
        )
    }

    suspend fun settFormueobjektdata(
        formueobjekt: FastEiendomSomFormuesobjekt,
    ) = transactional(dataSource) { tx -> settFormueobjektdata(tx, formueobjekt) }

    fun settFormueobjektdata(
        tx: Session,
        formueobjekt: FastEiendomSomFormuesobjekt,
    ) {
        @Language("SQL")
        val sql = """
            UPDATE $table
            SET formueobjekt = :formueobjekt::jsonb, sistOppdatert = now(), status = :status
            WHERE matrikkelenhetId = :id
        """.trimIndent()

        val matrikkelenhetId = requireNotNull(formueobjekt.identifikator?.matrikkelUnikIdentifikator) {
            "matrikkelenhetId is required"
        }
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

    fun settStatus(
        tx: Session,
        matrikkelenhetId: String,
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
