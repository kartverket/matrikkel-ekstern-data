package no.kartverket.oidc.tokenclient

import com.nimbusds.oauth2.sdk.http.HTTPRequest
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import java.io.IOException
import java.net.URI

object OidcDiscoveryClient {
    fun fetch(discoveryClient: String): OIDCProviderMetadata {
        val url = URI.create(discoveryClient)

        val request = HTTPRequest(HTTPRequest.Method.GET, url)
        request.connectTimeout = 10_000
        request.readTimeout = 5_000

        val response = request.send()

        if (!response.indicatesSuccess()) {
            throw IOException("Couldn't download OpenID Provider metadata from $url. Response: ${response.statusCode}")
        }

        return OIDCProviderMetadata.parse(response.bodyAsJSONObject)
    }
}
