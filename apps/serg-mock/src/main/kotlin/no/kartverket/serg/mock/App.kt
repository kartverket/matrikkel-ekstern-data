package no.kartverket.serg.mock

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.kartverket.ktor.KtorServer
import no.kartverket.ktor.Metrics
import no.kartverket.ktor.Selftest
import no.kartverket.serg.mock.infrastructure.ErrorHandler
import org.openapitools.client.infrastructure.Serializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*

val logger: Logger = LoggerFactory.getLogger("serg-mock")

fun main() {
    val config = SergMockConfig.fromEnv()
    val store = MockDataStore(maxGenerateBatch = config.maxGenerateBatch)

    logger.info("Starting serg-mock on port {} with max batch {}", config.httpPort, config.maxGenerateBatch)

    KtorServer
        .create(Netty, port = config.httpPort) {
            configureSergMockApp(config = config, store = store)
        }.start(wait = true)
}

internal fun Application.configureSergMockApp(
    config: SergMockConfig = SergMockConfig.fromEnv(),
    store: MockDataStore = MockDataStore(maxGenerateBatch = config.maxGenerateBatch),
) {
    install(XForwardedHeaders)
    install(ErrorHandler)
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Korrelasjonsid")
    }
    install(ContentNegotiation) {
        register(
            ContentType.Application.Json,
            JacksonConverter(
                objectMapper = Serializer.jacksonObjectMapper.apply {
                    setDefaultPrettyPrinter(
                        DefaultPrettyPrinter().apply {
                            indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                            indentObjectsWith(DefaultIndenter("  ", "\n"))
                        }
                    )
                    registerKotlinModule()
                },
                streamRequestBody = true
            )
        )
        jackson {
            findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    install(Metrics.Plugin)
    install(Selftest.Plugin)
    install(CallId) {
        generate { UUID.randomUUID().toString() }
        header(HttpHeaders.XCorrelationId)
        header("Korrelasjonsid")
    }
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        mdc("correlationId") { it.callId }
    }

    routing {
        get("/") {
            call.respondRedirect("/admin")
        }

        registerAdminRoutes(store = store, maxGenerateBatch = config.maxGenerateBatch)

        registerSergRoutes(store = store)
        route("/v1") {
            registerSergRoutes(store = store)
        }
    }
}
