package no.kartverket.matrikkel

import okhttp3.OkHttpClient
import org.openapitools.client.infrastructure.ApiClient

// openapi-generator (kotlin/jvm-okhttp4) fjerner ikke-JSON mediatyper fra Accept-headeren, og sender derfor
// "Accept: application/json" til /lastned-endepunktene. Brreg svarer da med 400 Bad Request.
// Dette er en workaround for buggen som er meldt inn; https://github.com/OpenAPITools/openapi-generator/issues/25733
internal val brregClient: OkHttpClient = ApiClient.defaultClient.newBuilder()
    .addInterceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/lastned")) {
            chain.proceed(request.newBuilder().header("Accept", "application/gzip").build())
        } else {
            chain.proceed(request)
        }
    }
    .build()