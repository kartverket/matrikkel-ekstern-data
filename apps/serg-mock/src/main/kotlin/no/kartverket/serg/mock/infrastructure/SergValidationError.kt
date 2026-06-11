package no.kartverket.serg.mock.infrastructure

import no.kartverket.serg.mock.validation.ValidationError

data class SergValidationError(
    val code: String,
    override val message: String,
) : ValidationError
