package no.kartverket.matrikkel.serg.repository

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import javax.sql.DataSource

class AvvikRepository(
    private val dataSource: DataSource,
    private val adminDataSource: DataSource,
) {
    companion object {
        private val table: String = "avvik"
        // Avvik innenfor perioden antas å være pga lag i synkroniseringen, og telles ikke med.
        private val gracePeriod: String = "INTERVAL '1 day'"
    }

    data class Avvik(
        val matrikkelenhetId: Long,
        val nr: String,
        val identType: IdentType,
        val eierforholdKodeId: Int,
        val type: AvvikType,
    ) {
        companion object
    }
    enum class AvvikType(val value: String) {
        MANGLER_I_M22("missing_in_matrikkelenhet_eiere"),
        EKSTRA_I_M22("extra_in_matrikkelenhet_eiere");

        companion object {
            private val map = entries.associateBy { it.value }
            fun fromDbValue(value: String) = map.getValue(value)
        }
    }
    enum class IdentType(val value: String) {
        FYSISK_PERSON("FysiskPerson"),
        JURIDISK_PERSON("JuridiskPerson"),
        ANNEN_PERSON("AnnenPerson");

        companion object {
            private val map = entries.associateBy { it.value }
            fun fromDbValue(value: String) = map.getValue(value)
        }
    }

    suspend fun antallAvvik(): Long = dataSource.withTransaction { tx ->
        antallAvvik(tx)
    }

    fun antallAvvik(tx: Session): Long {
        val query = queryOf("""
            SELECT COUNT(*) FROM $table a
            LEFT JOIN serg_dokument s ON a.id::bigint = s.matrikkelenhetid
            WHERE s.sistoppdatert IS NULL OR s.sistoppdatert < NOW() - $gracePeriod
        """.trimIndent())
            .map { it.long(1) }
            .asSingle

        return tx.run(query) ?: -1
    }

    suspend fun hentAvvik(): List<Avvik> = dataSource.withTransaction { tx ->
        hentAvvik(tx)
    }

    fun hentAvvik(tx: Session): List<Avvik> {
        val query = queryOf("""
            SELECT * FROM $table a
            LEFT JOIN serg_dokument s ON a.id::bigint = s.matrikkelenhetid
            WHERE s.sistoppdatert IS NULL OR s.sistoppdatert < NOW() - $gracePeriod
        """.trimIndent())
            .map(Avvik::fromRow)
            .asList

        return tx.run(query)
    }

    fun oppdaterAvvik() {
        adminDataSource.runSql { session ->
            session.run(queryOf("SELECT refresh_avvik()").asExecute)
        }
    }
}


private fun AvvikRepository.Avvik.Companion.fromRow(row: Row): AvvikRepository.Avvik {
    return AvvikRepository.Avvik(
        matrikkelenhetId = row.long("id"),
        nr = row.string("nr"),
        identType = AvvikRepository.IdentType.fromDbValue(row.string("ident_type")),
        eierforholdKodeId = row.int("eierforholdkodeid"),
        type = AvvikRepository.AvvikType.fromDbValue(row.string("diff_type")),
    )
}

