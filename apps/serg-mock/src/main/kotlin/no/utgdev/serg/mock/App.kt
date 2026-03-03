package no.utgdev.serg.mock

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.utgdev.ktor.KtorServer
import no.utgdev.ktor.Metrics
import no.utgdev.ktor.Selftest
import no.utgdev.serg.mock.infrastructure.ErrorHandler
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
        json(SergMockJson)
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
