package no.kartverket.oidc.tokenclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import no.kartverket.oidc.tokenclient.client.AzureAdMachineToMachineTokenClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AzureAdMachineToMachineTokenClientTest {
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
        val m2mToken = TokenCreator.createToken("subject")
        server.enqueue(tokenResponse(m2mToken))

        val tokenClient = AzureAdMachineToMachineTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        val token = tokenClient.createMachineToMachineToken("test-scope")
        val recordedRequest = server.takeRequest()

        val body = parseFormdata(recordedRequest.body.readUtf8())

        assertThat(token.serialize()).isEqualTo(m2mToken.serialize())
        assertThat(recordedRequest.path).isEqualTo("/token")
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(body["client_assertion_type"]).isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
        assertThat(body["audience"]).isEqualTo("test-scope")
        assertThat(body["grant_type"]).isEqualTo("client_credentials")
        assertThat(body["scope"]).isEqualTo("test-scope")
        assertThat(body["client_assertion"]).isNotNull()
    }

    @Test
    fun `should cache tokens`() {
        server.enqueue(tokenResponse(TokenCreator.createToken("subject1")))
        server.enqueue(tokenResponse(TokenCreator.createToken("subject2")))

        val tokenClient = AzureAdMachineToMachineTokenClient(
            clientId = "clientId",
            tokenEndpoint = server.url("/token").toString(),
            privateJwk = TokenCreator.jwk.toJSONString(),
            tokenCache = CaffeineTokenCache()
        )

        tokenClient.createMachineToMachineToken("scope-1")
        tokenClient.createMachineToMachineToken("scope-1")

        assertThat(server.requestCount).isEqualTo(1)

        tokenClient.createMachineToMachineToken("scope-2")
        assertThat(server.requestCount).isEqualTo(2)
    }
}
