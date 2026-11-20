package no.kartverket.matrikkel

import assertk.assertThat
import assertk.assertions.hasSize
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import no.kartverket.matrikkel.parseJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ParseJsonTest {
    @Serializable
    data class Dummy(
        val name: String
    )

    @Test
    fun `stream parsing til flow`(): Unit = runBlocking {
        val input = """
            [
              { "name": "Ola" },
              { "name": "Kari" }
            ]
        """.trimIndent()
        val stream = input.byteInputStream()

        val elements = stream.parseJson<Dummy>().toList()

        assertThat(elements).hasSize(2)
    }
}