package no.kartverket.oidc.tokenclient.client

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.JWTBearerGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import no.kartverket.oidc.tokenclient.CaffeineTokenCache
import no.kartverket.oidc.tokenclient.TokenCache
import no.kartverket.oidc.tokenclient.utils.TokenClientUtils
import org.slf4j.LoggerFactory

class MaskinportenMachineToMachineTokenClient(
    clientId: String,
    tokenEndpoint: String,
    privateJwk: String,
    tokenCache: TokenCache = CaffeineTokenCache(),
) : AbstractTokenClient(clientId, tokenEndpoint, privateJwk, tokenCache),
    MachineToMachineTokenClient {
    companion object {
        private val log = LoggerFactory.getLogger("MaskinportenMachineToMachineTokenClient")
    }

    override fun createMachineToMachineToken(scope: String): SignedJWT {
        val cacheKey = scope
        return tokenCache.getFromCacheOrTryProvider(cacheKey) { createToken(scope) }
    }

    private fun createToken(scope: String): SignedJWT {
        val signedJWT = TokenClientUtils.signedClientAssertion(
            TokenClientUtils.clientAssertionHeader(privateJwkKeyId),
            TokenClientUtils.clientAssertionClaimsWithScope(clientId, tokenEndpoint.toString(), scope),
            assertionSigner,
        )
        val request = TokenRequest.Builder(tokenEndpoint, signedJWT, JWTBearerGrant(signedJWT.clientAssertion))
            .scope(Scope(scope))
            .build()

        val response = TokenResponse.parse(request.toHTTPRequest().send())

        if (!response.indicatesSuccess()) {
            val error = response.toErrorResponse()
            val message = "Failed to fetch Maskinporten M2M token for scope=$scope"

            log.error("{}. Error: {}", message, error.toJSONObject().toString())
            throw RuntimeException(message)
        }

        return SignedJWT.parse(response.toSuccessResponse().tokens.accessToken.value)
    }
}
