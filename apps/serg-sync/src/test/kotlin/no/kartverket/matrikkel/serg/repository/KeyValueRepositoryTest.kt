package no.kartverket.matrikkel.serg.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class KeyValueRepositoryTest : WithDatabase {
    @Test
    fun `setting and retrieving values work`() = runBlocking {
        val repository = KeyValueRepository(dataSource())

        repository.setValue("key", "value")
        assertThat(repository.getValue("key")).isEqualTo("value")
    }

    @Test
    fun `getting missing key`() = runBlocking {
        val repository = KeyValueRepository(dataSource())

        assertThat(repository.getValueOrNull("missing")).isNull()
    }

    @Test
    fun `keys can be deleted`() = runBlocking {
        val repository = KeyValueRepository(dataSource())

        repository.setValue("key", "value")
        assertThat(repository.getValue("key")).isNotNull()

        repository.delete("key")
        assertThat(repository.getValueOrNull("key")).isNull()
    }
}