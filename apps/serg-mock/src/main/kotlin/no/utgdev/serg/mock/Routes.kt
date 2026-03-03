package no.utgdev.serg.mock

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.utgdev.ktor.validation.pathField
import no.utgdev.ktor.validation.queryField
import no.utgdev.serg.mock.infrastructure.FeilException
import no.utgdev.serg.mock.infrastructure.SergValidationError
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelser
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Start
import no.utgdev.validation.*
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

internal fun Route.registerSergRoutes(store: MockDataStore) {
    get("/hendelser") {
        val fraSekvensnummer: Long =
            call
                .queryField("fraSekvensnummer")
                .asLong(errorMsg("fraSekvensnummer must be a valid integer"))
                .positive(errorMsg("fraSekvensnummer must be > 0"))
                .default(0)
                .get()

        val antall: Int =
            call
                .queryField("antall")
                .asInt(errorMsg("antall must be a valid integer"))
                .positive(errorMsg("antall must be > 0"))
                .default(10)
                .get()

        val events = store.readEvents(fromSequence = fraSekvensnummer, limit = antall)

        call.respond(Hendelser(hendelser = events))
    }

    get("/hendelser/start") {
        val date =
            call
                .queryField("dato")
                .asIsoLocalDate(errorMsg("dato must use ISO format YYYY-MM-DD"))
                .required(errorMsg("dato must be provided"))
                .refine(
                    predicate = { parsed -> !parsed.isAfter(LocalDate.now(ZoneOffset.UTC)) },
                    error = errorMsg("dato cannot be in the future"),
                ).get()

        call.respond(Start(sekvensnummer = store.findStartSequence(date)))
    }

    get("/{rettighetspakke}/{hendelseidentifikator}") {
        val hendelseidentifikator =
            call
                .pathField("hendelseidentifikator")
                .required(errorMsg("Missing hendelseidentifikator"))
                .nonBlank(errorMsg("Missing hendelseidentifikator"))
                .get()

        val formuesobjekt =
            store.findFormuesobjekt(hendelseidentifikator)
                ?: throw FeilException(
                    httpCode = HttpStatusCode.NotFound,
                    kode = "MOCK-404",
                    melding = "Fant ikke hendelseidentifikator",
                    korrelasjonsid = call.correlationId(),
                )

        call.respond(formuesobjekt)
    }
}

internal fun Route.registerAdminRoutes(
    store: MockDataStore,
    maxGenerateBatch: Int,
) {
    get("/admin") {
        call.respondText(
            text = adminPageHtml(maxGenerateBatch = maxGenerateBatch),
            contentType = ContentType.Text.Html,
        )
    }

    route("/admin/api") {
        get("/state") {
            val snapshot = store.snapshot()
            call.respond(
                StateResponse(
                    totalEvents = snapshot.totalEvents,
                    nextSequence = snapshot.nextSequence,
                    presets = PresetName.entries.map { it.name },
                    recent = snapshot.recent,
                    firstSeq = snapshot.firstSeq,
                    lastSeq = snapshot.lastSeq,
                ),
            )
        }

        post("/generate") {
            val request =
                runCatching { call.receive<GenerateRequest>() }.getOrElse { cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AdminErrorResponse(message = cause.message ?: "Invalid JSON payload"),
                    )
                    return@post
                }

            val countErrorMessage = "count must be between 1 and $maxGenerateBatch"
            V
                .field(name = "count", value = request.count)
                .positive(errorMsg(countErrorMessage))
                .refine(
                    predicate = { it <= maxGenerateBatch },
                    error = errorMsg(countErrorMessage),
                ).get()

            val summary =
                runCatching {
                    store.generate(request)
                }.getOrElse { cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AdminErrorResponse(message = cause.message ?: "Invalid generate request"),
                    )
                    return@post
                }

            call.respond(
                GenerateResponse(
                    generated = summary.generated,
                    totalEvents = summary.totalEvents,
                    firstSeq = summary.firstSeq,
                    lastSeq = summary.lastSeq,
                ),
            )
        }

        post("/clear") {
            val summary = store.clear()
            call.respond(ClearResponse(clearedEvents = summary.clearedEvents, clearedObjects = summary.clearedObjects))
        }
    }
}

private fun ApplicationCall.correlationId(): String {
    return request.headers["Korrelasjonsid"]
        ?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()
}

private fun errorMsg(
    message: String,
    code: String = "MOCK-400",
): SergValidationError = SergValidationError(code = code, message = message)
