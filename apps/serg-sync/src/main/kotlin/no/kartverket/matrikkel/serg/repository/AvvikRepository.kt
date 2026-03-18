package no.kartverket.matrikkel.serg.repository

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import javax.sql.DataSource

class AvvikRepository(
    private val dataSource: DataSource,
    private val adminDataSource: DataSource,
) {
    private val table: String = "avvik"

    data class Avvik(
        val matrikkelenhetId: Long,
        val nr: String,
        val eierforholdKodeId: Int,
        val type: AvvikType
    ) {
        companion object
    }
    enum class AvvikType(val value: String) {
        MANGLER_I_M22("missing_in_matrikkelenhet_eiere"),
        EKSTRA_I_M22("extra_in_matrikkelenhet_eiere");

        companion object {
            private val map = entries.associateBy { it.value }
            fun fromDbValue(value: String): AvvikType = map.getValue(value)
        }
    }

    suspend fun hentAvvik(): List<Avvik> = dataSource.withTransaction { tx ->
        hentAvvik(tx)
    }

    fun hentAvvik(tx: Session): List<Avvik> {
        val query = queryOf("SELECT * FROM $table")
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
        eierforholdKodeId = row.int("eierforholdkodeid"),
        type = AvvikRepository.AvvikType.fromDbValue(row.string("diff_type"))
    )
}

