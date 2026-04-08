package no.kartverket.kotlin

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RetryTest {
    @Test
    fun `should return at once if success`() = runBlocking {
        val fn = mockk<() -> String>()
        every { fn.invoke() } returns "This is ok"

        val result = retry(attempts = 3, fn = fn)

        assertThat(result).isEqualTo("This is ok")
        verify(exactly = 1) { fn.invoke() }
    }

    @Test
    fun `should retry function N times`() = runBlocking {
        val fn = mockk<() -> String>()
        every { fn.invoke() } throws IllegalArgumentException("Not gikk feil")

        assertThrows<IllegalArgumentException> {
            retry(attempts = 3, fn = fn)
        }

        verify(exactly = 3) { fn.invoke() }
    }

    @Test
    fun `should bypass retry on known exceptions`() = runBlocking {
        val fn = mockk<() -> String>()
        every { fn.invoke() } throws IllegalArgumentException("Not gikk feil")

        assertThrows<IllegalArgumentException> {
            retry(
                attempts = 3,
                stopRetryIf = { it is IllegalArgumentException },
                fn = fn
            )
        }

        verify(exactly = 1) { fn.invoke() }
    }

    @Test
    fun `should retry until success`() = runBlocking {
        val fn = mockk<() -> String>()
        every { fn.invoke() } throws IllegalArgumentException("fail 1") andThenThrows
                IllegalArgumentException("fail 2") andThen
                "ok"

        val result = retry(attempts = 3, fn = fn)

        assertThat(result).isEqualTo("ok")
        verify(exactly = 3) { fn.invoke() }
    }
}