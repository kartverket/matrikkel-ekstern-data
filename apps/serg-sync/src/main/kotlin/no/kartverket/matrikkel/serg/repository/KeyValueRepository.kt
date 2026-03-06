package no.kartverket.matrikkel.serg.repository

import kotliquery.Session
import kotliquery.queryOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class KeyValueRepository(
    private val dataSource: DataSource,
) {
    private val table: String = "keyvalue"

    suspend fun getValue(key: String): String = requireNotNull(getValueOrNull(key))

    suspend fun getValueOrNull(key: String): String? = transactional(dataSource) { tx ->
        getValueOrNull(tx, key)
    }
    fun getValueOrNull(tx: Session, key: String): String? {
        return tx.run(
            queryOf("SELECT * FROM $table WHERE key = :key", mapOf("key" to key))
                .map {
                    it.stringOrNull("value")
                }.asSingle,
        )
    }

    suspend fun setValue(
        key: String,
        value: String,
    ) = transactional(dataSource) { tx -> setValue(tx, key, value)
    }
    fun setValue(
        tx: Session,
        key: String,
        value: String,
    ) {
        @Language("SQL")
        val sql =
            """
            INSERT INTO $table (key, value)
            VALUES (:key, :value)
            ON CONFLICT (key)
            DO UPDATE SET value = EXCLUDED.value
            """.trimIndent()

        tx.run(
            queryOf(
                sql,
                mapOf(
                    "key" to key,
                    "value" to value,
                ),
            ).asExecute,
        )
    }

    suspend fun delete(key: String) {
        @Language("SQL")
        val sql =
            """
            DELETE FROM $table where key = :key
            """.trimIndent()

        transactional(dataSource) { tx ->
            tx.run(
                queryOf(
                    sql,
                    mapOf(
                        "key" to key,
                    ),
                ).asExecute,
            )
        }
    }
}
