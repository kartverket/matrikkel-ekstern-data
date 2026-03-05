package no.kartverket.oidc.tokenclient

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.nimbusds.jwt.SignedJWT
import no.kartverket.oidc.tokenclient.client.AzureAdOnBehalfOfTokenClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.ParseException

class AzureAdOnBehalfOfTokenClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `validate request and response parsing`() {
        val oboToken = TokenCreator.createToken("subject")
        server.enqueue(tokenResponse(oboToken))

        val tokenClient = AzureAdOnBehalfOfTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        val userAccesstoken = TokenCreator.createToken("subject")
        val token = tokenClient.exchangeOnBehalfOfToken("test-scope", userAccesstoken)
        val recordedRequest = server.takeRequest()

        val body = parseFormdata(recordedRequest.body.readUtf8())

        assertThat(token.serialize()).isEqualTo(oboToken.serialize())
        assertThat(recordedRequest.path).isEqualTo("/token")
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(body["client_assertion_type"]).isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
        assertThat(body["audience"]).isEqualTo("test-scope")
        assertThat(body["grant_type"]).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer")
        assertThat(body["scope"]).isEqualTo("test-scope")
        assertThat(body["client_assertion"]).isNotNull()
    }

    @Test
    fun `should cache tokens`() {
        server.enqueue(tokenResponse(TokenCreator.createToken("subject1")))
        server.enqueue(tokenResponse(TokenCreator.createToken("subject2")))
        server.enqueue(tokenResponse(TokenCreator.createToken("subject2")))

        val tokenClient = AzureAdOnBehalfOfTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        val user1Token1 = TokenCreator.createToken("subject1")
        val user1Token2 = TokenCreator.createToken("subject1")
        val user2Token1 = TokenCreator.createToken("subject2")

        tokenClient.exchangeOnBehalfOfToken("test-scope-1", user1Token1)
        tokenClient.exchangeOnBehalfOfToken("test-scope-1", user1Token1)
        tokenClient.exchangeOnBehalfOfToken("test-scope-1", user1Token2)

        assertThat(server.requestCount).isEqualTo(1)

        tokenClient.exchangeOnBehalfOfToken("test-scope-1", user2Token1)
        tokenClient.exchangeOnBehalfOfToken("test-scope-2", user2Token1)

        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `should throw exception if subject is missing in accesstoken`() {
        val tokenClient = AzureAdOnBehalfOfTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        assertFailure {
            @Suppress("CAST_NEVER_SUCCEEDS")
            tokenClient.exchangeOnBehalfOfToken("scope", TokenCreator.createToken(null))
        }.hasMessage("Unable to get subject from access token")
    }

    @Test
    fun `should throw exception if accesstoken is invalid`() {
        val tokenClient = AzureAdOnBehalfOfTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        assertFailure {
            @Suppress("CAST_NEVER_SUCCEEDS")
            tokenClient.exchangeOnBehalfOfToken("scope", SignedJWT.parse("Not a valid accesstoken"))
        }.isInstanceOf<ParseException>()
    }
}
