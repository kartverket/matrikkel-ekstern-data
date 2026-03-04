package no.kartverket.oidc.tokenclient.utils

import com.nimbusds.jwt.SignedJWT

object TokenUtils {
    fun getSubject(token: SignedJWT): String {
        return requireNotNull(token.jwtClaimsSet.subject) {
            "Unable to get subject from access token"
        }
    }
}
