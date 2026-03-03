package no.kartverket.serg.mock.infrastructure

import no.kartverket.validation.ValidationError

data class SergValidationError(
    val code: String,
    override val message: String,
) : ValidationError
