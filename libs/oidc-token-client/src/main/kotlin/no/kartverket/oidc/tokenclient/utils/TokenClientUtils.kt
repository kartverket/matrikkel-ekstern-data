package no.kartverket.oidc.tokenclient.utils

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTClaimsSet.Builder
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.auth.PrivateKeyJWT
import java.util.Date
import java.util.UUID

object TokenClientUtils {
    fun signedClientAssertion(
        header: JWSHeader,
        claims: JWTClaimsSet,
        signer: JWSSigner
    ): PrivateKeyJWT {
        val signedJWT = SignedJWT(header, claims).apply { sign(signer) }
        return PrivateKeyJWT(signedJWT)
    }

    fun clientAssertionHeader(keyId: String): JWSHeader {
        return JWSHeader.parse(
            mapOf(
                "kid" to keyId,
                "typ" to "JWT",
                "alg" to "RS256"
            )
        )
    }

    fun clientAssertionClaims(clientId: String, audience: String): JWTClaimsSet {
        return clientAssertionClaimsBuilder(clientId, audience).build()
    }

    fun clientAssertionClaimsWithScope(clientId: String, audience: String, scope: String): JWTClaimsSet {
        return clientAssertionClaimsBuilder(clientId, audience).claim("scope", scope).build()
    }

    private fun clientAssertionClaimsBuilder(clientId: String, audience: String): Builder {
        val now = Date()
        val expiration = Date(now.toInstant().plusSeconds(30).toEpochMilli())

        return Builder()
            .subject(clientId)
            .issuer(clientId)
            .audience(audience)
            .jwtID(UUID.randomUUID().toString())
            .issueTime(now)
            .notBeforeTime(now)
            .expirationTime(expiration)
    }
}
