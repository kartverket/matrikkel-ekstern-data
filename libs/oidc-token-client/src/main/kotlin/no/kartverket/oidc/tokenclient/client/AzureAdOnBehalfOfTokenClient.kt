package no.kartverket.oidc.tokenclient.client

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.JWTBearerGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import no.kartverket.oidc.tokenclient.TokenCache
import no.kartverket.oidc.tokenclient.utils.TokenClientUtils.clientAssertionClaims
import no.kartverket.oidc.tokenclient.utils.TokenClientUtils.clientAssertionHeader
import no.kartverket.oidc.tokenclient.utils.TokenClientUtils.signedClientAssertion
import no.kartverket.oidc.tokenclient.utils.TokenUtils.getSubject
import org.slf4j.LoggerFactory

class AzureAdOnBehalfOfTokenClient(
    clientId: String,
    tokenEndpoint: String,
    privateJwk: String,
    tokenCache: TokenCache
) : AbstractTokenClient(clientId, tokenEndpoint, privateJwk, tokenCache), OnBehalfOfTokenClient {
    companion object {
        private val log = LoggerFactory.getLogger("AzureAdMachineToMachineTokenClient")
    }

    override fun exchangeOnBehalfOfToken(scope: String, accessToken: SignedJWT): SignedJWT {
        val cacheKey = "$scope-${getSubject(accessToken)}"
        return tokenCache.getFromCacheOrTryProvider(cacheKey) { createToken(scope, accessToken) }
    }

    private fun createToken(scope: String, accessToken: SignedJWT): SignedJWT {
        val signedJWT = signedClientAssertion(
            clientAssertionHeader(privateJwkKeyId),
            clientAssertionClaims(this@AzureAdOnBehalfOfTokenClient.clientId, tokenEndpoint.toString()),
            assertionSigner
        )

        val request = TokenRequest.Builder(tokenEndpoint, signedJWT, JWTBearerGrant(accessToken))
            .scope(Scope(scope))
            .customParameter("audience", scope)
            .customParameter("subject_token", accessToken.parsedString)
            .customParameter("requested_token_use", "on_behalf_of")
            .customParameter("subject_token_type", "urn:ietf:params:oauth:token-type:jwt") // TODO sjekk om denne trengs
            .build()

        val response = TokenResponse.parse(request.toHTTPRequest().send())

        if (!response.indicatesSuccess()) {
            val error = response.toErrorResponse()
            val message = "Failed to fetch AzureAD OBO token for scope=$scope"

            log.error("{}. Error: {}", message, error.toJSONObject().toString())
            throw RuntimeException(message)
        }

        return SignedJWT.parse(response.toSuccessResponse().tokens.accessToken.value)
    }
}
