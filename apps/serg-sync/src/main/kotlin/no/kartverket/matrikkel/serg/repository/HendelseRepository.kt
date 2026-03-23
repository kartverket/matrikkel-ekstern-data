package no.kartverket.matrikkel.serg.repository

import kotliquery.Session
import kotliquery.queryOf
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.intellij.lang.annotations.Language
import org.openapitools.client.infrastructure.Serializer
import javax.sql.DataSource

class HendelseRepository(
    private val dataSource: DataSource,
) {
    private val format = Serializer.jacksonObjectMapper

    suspend fun insert(hendelse: Hendelse) = dataSource.withTransaction { tx ->
        insert(tx, hendelse)
    }

    fun insert(
        tx: Session,
        hendelse: Hendelse,
    ) {
        checkNotNull(hendelse.sekvensnummer)

        @Language("SQL")
        val sql =
            """
            INSERT INTO hendelse (sekvensnummer, hendelse)
            VALUES (:sekvensnummer, :hendelse::jsonb)
            ON CONFLICT (sekvensnummer) DO NOTHING;
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "sekvensnummer" to hendelse.sekvensnummer,
                    "hendelse" to format.writeValueAsString(hendelse),
                ),
            ).asUpdate,
        )
    }
}
