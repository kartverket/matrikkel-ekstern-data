package no.kartverket.serg.mock.infrastructure

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import no.kartverket.serg.mock.AdminErrorResponse
import no.kartverket.serg.mock.logger
import no.kartverket.validation.ValidationException
import java.util.UUID
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Feil as HendelserFeil

class FeilException(
    val httpCode: HttpStatusCode,
    val kode: String,
    val melding: String,
    val korrelasjonsid: String,
) : Exception()

val ErrorHandler =
    createApplicationPlugin("ErrorHandler") {
        with(application) {
            install(StatusPages) {
                exception<FeilException> { call, cause ->
                    call.respond(
                        cause.httpCode,
                        HendelserFeil(
                            kode = cause.kode,
                            melding = cause.melding,
                            korrelasjonsid = cause.korrelasjonsid,
                        ),
                    )
                }
                exception<ValidationException> { call, cause ->
                    if (call.isAdminPath()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            AdminErrorResponse(message = cause.error.message),
                        )
                    } else {
                        val payload = cause.error
                        val code = (payload as? SergValidationError)?.code ?: "MOCK-400"
                        call.respond(
                            HttpStatusCode.BadRequest,
                            HendelserFeil(
                                kode = code,
                                melding = payload.message,
                                korrelasjonsid = call.correlationId(),
                            ),
                        )
                    }
                }
                exception<IllegalArgumentException> { call, cause ->
                    if (call.isAdminPath()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            AdminErrorResponse(message = cause.message ?: "Invalid request"),
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            HendelserFeil(
                                kode = "MOCK-400",
                                melding = cause.message ?: "Invalid request",
                                korrelasjonsid = call.correlationId(),
                            ),
                        )
                    }
                }
                exception<Throwable> { call, cause ->
                    logger.error("Unhandled error", cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AdminErrorResponse(message = "Internal server error"),
                    )
                }
            }
        }
    }

private fun ApplicationCall.isAdminPath(): Boolean {
    val requestPath = request.path()
    return requestPath == "/admin" || requestPath.startsWith("/admin/")
}

private fun ApplicationCall.correlationId(): String = request.headers["Korrelasjonsid"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
