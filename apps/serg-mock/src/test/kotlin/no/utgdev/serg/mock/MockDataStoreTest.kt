package no.utgdev.serg.mock

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test

class MockDataStoreTest {
    @Test
    fun `generate creates ascending sequence and id mapping`() {
        val store = MockDataStore(maxGenerateBatch = 10_000)

        val summary = store.generate(GenerateRequest(count = 3, preset = PresetName.BALANCED))

        assertThat(summary.generated).isEqualTo(3)
        assertThat(summary.firstSeq).isEqualTo(1L)
        assertThat(summary.lastSeq).isEqualTo(3L)

        val events = store.readEvents(fromSequence = 0, limit = 10)
        assertThat(events.mapNotNull { it.sekvensnummer }).isEqualTo(listOf(1L, 2L, 3L))

        val firstId = events.first().hendelseidentifikator.toString()
        val mapped = store.findFormuesobjekt(firstId)
        assertThat(mapped).isNotNull()
        assertThat(mapped?.hendelsesidentifikator.toString()).isEqualTo(firstId)
    }

    @Test
    fun `preset distributions are deterministic and biased`() {
        val generator = FakeDataGenerator()

        val balanced = (1L..300L).groupingBy { generator.eventTypeFor(it, PresetName.BALANCED).value }.eachCount()
        val newHeavy = (1L..300L).groupingBy { generator.eventTypeFor(it, PresetName.NEW_HEAVY).value }.eachCount()
        val deleteHeavy = (1L..300L).groupingBy { generator.eventTypeFor(it, PresetName.DELETE_HEAVY).value }.eachCount()

        assertThat(balanced["ny"]).isNotNull()
        assertThat(balanced["endret"]).isNotNull()
        assertThat(balanced["slettet"]).isNotNull()

        assertThat(newHeavy.getValue("ny")).isGreaterThan(newHeavy.getValue("endret"))
        assertThat(newHeavy.getValue("endret")).isGreaterThan(newHeavy.getValue("slettet"))

        assertThat(deleteHeavy.getValue("slettet")).isGreaterThan(deleteHeavy.getValue("endret"))
        assertThat(deleteHeavy.getValue("endret")).isGreaterThan(deleteHeavy.getValue("ny"))
    }

    @Test
    fun `clear resets all state including sequence counter`() {
        val store = MockDataStore(maxGenerateBatch = 10_000)
        store.generate(GenerateRequest(count = 5, preset = PresetName.BALANCED))

        val clear = store.clear()
        assertThat(clear.clearedEvents).isEqualTo(5)
        assertThat(clear.clearedObjects).isEqualTo(5)
        assertThat(store.snapshot().nextSequence).isEqualTo(1L)

        store.generate(GenerateRequest(count = 1, preset = PresetName.BALANCED))
        val sequence = store.readEvents(fromSequence = 0, limit = 10).single().sekvensnummer
        assertThat(sequence).isEqualTo(1L)
    }
}
