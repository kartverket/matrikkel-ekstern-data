package no.kartverket.oidc.tokenclient.client

import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import no.kartverket.oidc.tokenclient.TokenCache
import java.net.URI

class DownstreamApi(
    val cluster: String,
    val namespace: String,
    val application: String,
) {
    companion object {
        @JvmStatic
        fun parse(value: String): DownstreamApi {
            val parts = value.split(":")
            check(parts.size == 3) { "DownstreamApi string must contain 3 parts" }

            val (cluster, namespace, application) = parts
            return DownstreamApi(cluster = cluster, namespace = namespace, application = application)
        }
    }

    fun tokenscope(): String = "api://$cluster.$namespace.$application/.default"
}

fun interface MachineToMachineTokenClient {
    fun createMachineToMachineToken(scope: String): SignedJWT

    fun createMachineToMachineToken(scope: DownstreamApi): SignedJWT = createMachineToMachineToken(scope.tokenscope())

    fun bindTo(scope: String): BoundMachineToMachineTokenClient =
        object : BoundMachineToMachineTokenClient {
            override fun createToken(): SignedJWT = createMachineToMachineToken(scope)
        }

    fun bindTo(scope: DownstreamApi): BoundMachineToMachineTokenClient = bindTo(scope.tokenscope())
}

fun interface BoundMachineToMachineTokenClient {
    fun createToken(): SignedJWT
}

fun interface OnBehalfOfTokenClient {
    fun exchangeOnBehalfOfToken(
        scope: String,
        accessToken: SignedJWT,
    ): SignedJWT

    fun exchangeOnBehalfOfToken(
        scope: DownstreamApi,
        accessToken: SignedJWT,
    ): SignedJWT = exchangeOnBehalfOfToken(scope.tokenscope(), accessToken)

    fun bindTo(scope: String): BoundOnBehalfOfTokenClient =
        object : BoundOnBehalfOfTokenClient {
            override fun exchangeToken(accessToken: SignedJWT): SignedJWT = exchangeOnBehalfOfToken(scope, accessToken)
        }

    fun bindTo(scope: DownstreamApi): BoundOnBehalfOfTokenClient = bindTo(scope.tokenscope())
}

fun interface BoundOnBehalfOfTokenClient {
    fun exchangeToken(accessToken: SignedJWT): SignedJWT
}

abstract class AbstractTokenClient(
    protected val clientId: String,
    tokenEndpoint: String,
    privateJwk: String,
    protected val tokenCache: TokenCache,
) {
    protected val tokenEndpoint: URI = URI.create(tokenEndpoint)
    protected val privateJwkKeyId: String
    protected val assertionSigner: JWSSigner

    init {
        val rsaKey = RSAKey.parse(privateJwk)

        privateJwkKeyId = rsaKey.keyID
        assertionSigner = RSASSASigner(rsaKey)
    }
}
