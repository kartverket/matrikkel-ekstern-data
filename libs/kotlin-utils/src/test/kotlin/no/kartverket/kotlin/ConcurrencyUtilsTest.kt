package no.kartverket.kotlin

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ConcurrencyUtilsTest {
    @Test
    fun `should never exceed parallellism`() = runBlocking {
        var active = 0
        val list = (10..20).toList()

        val result = list.mapInParallell(2) { value, index ->
            val currentNumberOfAction = ++active
            if (currentNumberOfAction > 2) fail {
                "We should never exceed paralellism limit"
            }

            // Delay invers of index to make out-of-order completion
            delay((list.size - index) * 100L)
            (value * 2).also {
                active--
            }
        }

        // Results should still be as if standard map (e.g in-order)
        val expected = list.map { it * 2 }
        assertThat(result).isEqualTo(expected)
    }
}