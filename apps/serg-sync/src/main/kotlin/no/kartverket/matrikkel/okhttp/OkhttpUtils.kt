package no.kartverket.matrikkel.okhttp

import okhttp3.Interceptor
import okhttp3.Response

object OkHttpUtils {
    open class HeadersInterceptor(
        val headersProvider: () -> Map<String, String>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder =
                chain
                    .request()
                    .newBuilder()
            headersProvider()
                .forEach { (name, value) -> builder.addHeader(name, value) }

            return chain.proceed(builder.build())
        }
    }

    class AuthorizationInterceptor(
        tokenProvider: () -> String,
    ) : HeadersInterceptor({
            mapOf("Authorization" to "Bearer ${tokenProvider()}")
        })
}
