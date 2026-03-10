package no.kartverket.matrikkel.okhttp

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object OkHttpUtils {
    fun OkHttpClient.Builder.addInterceptorAtStart(interceptor: Interceptor): OkHttpClient.Builder {
        this.interceptors().add(0, interceptor)
        return this
    }

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

    class MetricsInterceptor(
        private val name: String,
        private val registry: MeterRegistry
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val start = System.nanoTime()

            return try {
                val result = chain.proceed(request)
                request
                    .reportMetrics {
                        tag("status", result.code.toString())
                        tag("exception", "")
                    }
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS)

                result
            } catch (e: IOException) {
                request
                    .reportMetrics {
                        tag("status", "IO_ERROR")
                        tag("exception", e.javaClass.simpleName)
                    }
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS)

                throw e
            }
        }

        private fun Request.reportMetrics(block: Timer.Builder.() -> Unit): Timer {
            return Timer.builder("http_client_requests")
                .tag("name", name)
                .tag("method", this.method)
                .serviceLevelObjectives(
                    100.milliseconds.toJavaDuration(),
                    500.milliseconds.toJavaDuration(),
                    1.seconds.toJavaDuration(),
                    2.seconds.toJavaDuration(),
                    5.seconds.toJavaDuration(),
                )
                .apply(block)
                .register(registry)
        }
    }
}
