package no.kartverket.oidc.tokenclient

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class TokenCacheTest {
    @Test
    fun `should cache with same key`() {
        val cache: TokenCache = CaffeineTokenCache()

        var invocationCounter = 0
        val tokenProvider = {
            invocationCounter++
            TokenCreator.createToken("subject1")
        }
        cache.getFromCacheOrTryProvider("key1", tokenProvider)
        cache.getFromCacheOrTryProvider("key1", tokenProvider)

        assertThat(invocationCounter).isEqualTo(1)
    }

    @Test
    fun `should not cache different keys`() {
        val cache: TokenCache = CaffeineTokenCache()

        var invocationCounter = 0
        val tokenProvider = {
            invocationCounter++
            TokenCreator.createToken("subject1")
        }
        cache.getFromCacheOrTryProvider("key1", tokenProvider)
        cache.getFromCacheOrTryProvider("key2", tokenProvider)
        cache.getFromCacheOrTryProvider("key1", tokenProvider)

        assertThat(invocationCounter).isEqualTo(2)
    }

    @Test
    fun `should evict early based on configration`() {
        val cache = CaffeineTokenCache(Duration.ofSeconds(10))
        var invocationCounter = 0
        val tokenProvider = {
            invocationCounter++
            TokenCreator.createToken("subject1", expiry = 12.seconds)
        }

        val beforeExpiry = arrayOf(
            cache.getFromCacheOrTryProvider("key1", tokenProvider),
            cache.getFromCacheOrTryProvider("key1", tokenProvider)
        )
        Thread.sleep(3.seconds.inWholeMilliseconds)

        val afterExpiry = arrayOf(
            cache.getFromCacheOrTryProvider("key1", tokenProvider),
            cache.getFromCacheOrTryProvider("key1", tokenProvider)
        )

        assertThat(invocationCounter).isEqualTo(2)
        assertThat(beforeExpiry.toSet()).hasSize(1)
        assertThat(afterExpiry.toSet()).hasSize(1)
        assertThat(beforeExpiry.first()).isNotEqualTo(afterExpiry.first())
    }
}
