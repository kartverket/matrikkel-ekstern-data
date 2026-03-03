package no.kartverket.ktor.validation

import io.ktor.server.application.ApplicationCall
import no.kartverket.validation.Field
import no.kartverket.validation.V

fun ApplicationCall.queryField(name: String): Field<String?> = V.field(name = name, value = request.queryParameters[name])

fun ApplicationCall.pathField(name: String): Field<String?> = V.field(name = name, value = parameters[name])

fun ApplicationCall.headerField(name: String): Field<String?> = V.field(name = name, value = request.headers[name])
