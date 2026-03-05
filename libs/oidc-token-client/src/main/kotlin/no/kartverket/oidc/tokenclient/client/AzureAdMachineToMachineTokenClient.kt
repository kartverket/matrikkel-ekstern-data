package no.kartverket.oidc.tokenclient.client

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import no.kartverket.oidc.tokenclient.TokenCache
import no.kartverket.oidc.tokenclient.utils.TokenClientUtils
import org.slf4j.LoggerFactory

class AzureAdMachineToMachineTokenClient(
    clientId: String,
    tokenEndpoint: String,
    privateJwk: String,
    tokenCache: TokenCache
) : AbstractTokenClient(clientId, tokenEndpoint, privateJwk, tokenCache), MachineToMachineTokenClient {
    companion object {
        private val log = LoggerFactory.getLogger("AzureAdMachineToMachineTokenClient")
    }

    override fun createMachineToMachineToken(scope: String): SignedJWT {
        val cacheKey = scope
        return tokenCache.getFromCacheOrTryProvider(cacheKey) { createToken(scope) }
    }

    private fun createToken(scope: String): SignedJWT {
        val signedJWT = TokenClientUtils.signedClientAssertion(
            TokenClientUtils.clientAssertionHeader(privateJwkKeyId),
            TokenClientUtils.clientAssertionClaims(clientId, tokenEndpoint.toString()),
            assertionSigner
        )

        val request = TokenRequest.Builder(tokenEndpoint, signedJWT, ClientCredentialsGrant())
            .scope(Scope(scope))
            .customParameter("audience", scope)
            .build()

        val response = TokenResponse.parse(request.toHTTPRequest().send())

        if (!response.indicatesSuccess()) {
            val error = response.toErrorResponse()
            val message = "Failed to fetch AzureAD M2M token for scope=$scope"

            log.error("{}. Error: {}", message, error.toJSONObject().toString())
            throw RuntimeException(message)
        }

        return SignedJWT.parse(response.toSuccessResponse().tokens.accessToken.value)
    }
}
