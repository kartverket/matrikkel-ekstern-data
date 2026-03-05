package no.kartverket.oidc.tokenclient.utils

object AzureAdEnvironmentVariables {
    const val CLIENT_ID = "AZURE_APP_CLIENT_ID"
    const val CLIENT_SECRET = "AZURE_CLIENT_SECRET"
    const val APP_JWK = "AZURE_APP_JWK"
    const val APP_JWKS = "AZURE_APP_JWKS"
    const val APP_TENANT_ID = "AZURE_APP_TENANT_ID"
    const val APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
    const val OPENID_CONFIG_ISSUER = "AZURE_OPENID_CONFIG_ISSUER"
    const val OPENID_CONFIG_JWKS_URI = "AZURE_OPENID_CONFIG_JWKS_URI"
    const val OPENID_CONFIG_TOKEN_ENDPOINT = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"
}
