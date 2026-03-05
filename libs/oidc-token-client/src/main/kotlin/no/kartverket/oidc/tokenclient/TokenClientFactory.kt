package no.kartverket.oidc.tokenclient

import no.kartverket.oidc.tokenclient.client.AzureAdMachineToMachineTokenClient
import no.kartverket.oidc.tokenclient.client.AzureAdOnBehalfOfTokenClient
import no.kartverket.oidc.tokenclient.client.MachineToMachineTokenClient
import no.kartverket.oidc.tokenclient.client.OnBehalfOfTokenClient
import no.kartverket.oidc.tokenclient.utils.AzureAdEnvironmentVariables as AzureEnv

object TokenClientFactory {
    @JvmStatic
    fun createMachineToMachineTokenClient(): MachineToMachineTokenClient = createMachineToMachineTokenClient(CaffeineTokenCache())

    @JvmStatic
    fun createMachineToMachineTokenClient(tokenCache: TokenCache): MachineToMachineTokenClient =
        AzureAdMachineToMachineTokenClient(
            clientId = getEnv(AzureEnv.CLIENT_ID),
            tokenEndpoint = getEnv(AzureEnv.OPENID_CONFIG_TOKEN_ENDPOINT),
            privateJwk = getEnv(AzureEnv.APP_JWK),
            tokenCache = tokenCache,
        )

    @JvmStatic
    fun createOnBehalfOfTokenClient(): OnBehalfOfTokenClient = createOnBehalfOfTokenClient(CaffeineTokenCache())

    @JvmStatic
    fun createOnBehalfOfTokenClient(tokenCache: TokenCache): OnBehalfOfTokenClient =
        AzureAdOnBehalfOfTokenClient(
            clientId = getEnv(AzureEnv.CLIENT_ID),
            tokenEndpoint = getEnv(AzureEnv.OPENID_CONFIG_TOKEN_ENDPOINT),
            privateJwk = getEnv(AzureEnv.APP_JWK),
            tokenCache = tokenCache,
        )

    private fun getEnv(name: String): String {
        val value = requireNotNull(System.getProperty(name, System.getenv(name))) { "$name is required" }
        require(value.isNotEmpty()) { "$name must have a value" }
        return value
    }
}
