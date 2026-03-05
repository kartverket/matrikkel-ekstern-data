@file:OptIn(ExperimentalContracts::class)

package no.kartverket.validation

import kotlin.contracts.ExperimentalContracts

data class ValidationException(
    val field: String,
    val error: ValidationError,
) : RuntimeException(error.message)

interface ValidationError {
    val message: String
}

data class StringError(
    override val message: String,
) : ValidationError

class Field<T>(
    val name: String,
    private val value: T,
) {
    fun get(): T = value
}

object V {
    fun <T> field(
        name: String,
        value: T,
    ): Field<T> = Field(name = name, value = value)
}

fun <T : Any> Field<T?>.required(error: ValidationError): Field<T> {
    val current = get() ?: validationError(field = name, error = error)
    return Field(name = name, value = current)
}

@JvmName("requiredMessage")
fun <T : Any> Field<T?>.required(message: String): Field<T> = required(StringError(message))

fun <T : Any> Field<T?>.default(value: T): Field<T> = Field(name = name, value = get() ?: value)

fun <T> Field<T>.refine(
    predicate: (T) -> Boolean,
    error: ValidationError,
): Field<T> {
    val current = get()
    if (!predicate(current)) {
        validationError(field = name, error = error)
    }
    return this
}

@JvmName("refineMessage")
fun <T> Field<T>.refine(
    predicate: (T) -> Boolean,
    message: String,
): Field<T> = refine(predicate = predicate, error = StringError(message))

internal fun validationError(
    field: String,
    error: ValidationError,
): Nothing = throw ValidationException(field = field, error = error)
