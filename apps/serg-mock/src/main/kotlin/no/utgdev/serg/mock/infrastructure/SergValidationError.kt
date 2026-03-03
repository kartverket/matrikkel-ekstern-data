package no.utgdev.serg.mock.infrastructure

import no.utgdev.validation.ValidationError

data class SergValidationError(
    val code: String,
    override val message: String,
) : ValidationError
