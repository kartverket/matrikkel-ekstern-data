package no.kartverket.oidc.tokenclient

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import okhttp3.mockwebserver.MockResponse
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Date
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal fun tokenResponse(token: JWT): MockResponse = MockResponse()
    .setHeader("Content-Type", "application/json")
    .setBody("""{ "token_type": "Bearer", "access_token": "${token.serialize()}", "expires": 3600 }""")

internal fun parseFormdata(formdata: String): Map<String, String> {
    return formdata.split("&").associate {
        val (key, value) = it.split("=")
        key to URLDecoder.decode(value, UTF_8)
    }
}

object TokenCreator {
    val jwk: RSAKey = RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .generate()

    val signer: JWSSigner = RSASSASigner(jwk)

    fun createToken(subject: String?, expiry: Duration = 1.hours): SignedJWT {
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("https://example.com")
            .jwtID(UUID.randomUUID().toString())
            .expirationTime(Date(Date().time + expiry.inWholeMilliseconds))
            .build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwk.keyID).build(),
            claims
        ).also { it.sign(signer) }
    }
}
