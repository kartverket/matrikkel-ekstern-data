package no.kartverket.oidc.tokenclient

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.SignedJWT
import java.time.Duration

fun interface TokenCache {
    fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: () -> SignedJWT): SignedJWT
}

class CaffeineTokenCache(
    private val earlyRefreshThreshold: Duration = Duration.ofSeconds(30),
    private val cache: Cache<String, SignedJWT> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .build()
) : TokenCache {
    companion object {
        fun shouldRefreshToken(token: JWT, earlyRefreshThreshold: Duration): Boolean {
            return runCatching {
                val expiration = token.jwtClaimsSet.expirationTime
                if (expiration == null) return true

                val earlyExpiration = expiration.time - earlyRefreshThreshold.toMillis()

                return System.currentTimeMillis() > earlyExpiration
            }.getOrDefault(true)
        }
    }

    override fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: () -> SignedJWT): SignedJWT {
        val token = cache.getIfPresent(cacheKey)

        return when {
            token == null -> updatedToken(cacheKey, tokenProvider)
            shouldRefreshToken(token, earlyRefreshThreshold) -> updatedToken(cacheKey, tokenProvider)
            else -> token
        }
    }

    private fun updatedToken(cacheKey: String, tokenProvider: () -> SignedJWT): SignedJWT {
        val newToken = tokenProvider()
        cache.put(cacheKey, newToken)
        return newToken
    }
}
