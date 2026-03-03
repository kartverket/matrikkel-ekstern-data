package no.kartverket.validation

import java.time.LocalDate

fun Field<String>.asInt(error: ValidationError): Field<Int> {
    val parsed = get().toIntOrNull() ?: validationError(field = name, error = error)
    return Field(name = name, value = parsed)
}

@JvmName("asIntMessage")
fun Field<String>.asInt(error: String): Field<Int> = asInt(StringError(error))

@JvmName("asIntNullable")
fun Field<String?>.asInt(error: ValidationError): Field<Int?> {
    val current = get() ?: return Field(name = name, value = null)
    val parsed = current.toIntOrNull() ?: validationError(field = name, error = error)
    return Field(name = name, value = parsed)
}

@JvmName("asIntNullableMessage")
fun Field<String?>.asInt(message: String): Field<Int?> = asInt(StringError(message))

fun Field<String>.asLong(error: ValidationError): Field<Long> {
    val parsed = get().toLongOrNull() ?: validationError(field = name, error = error)
    return Field(name = name, value = parsed)
}

@JvmName("asLongMessage")
fun Field<String>.asLong(message: String): Field<Long> = asLong(StringError(message))

@JvmName("asLongNullable")
fun Field<String?>.asLong(error: ValidationError): Field<Long?> {
    val current = get() ?: return Field(name = name, value = null)
    val parsed = current.toLongOrNull() ?: validationError(field = name, error = error)
    return Field(name = name, value = parsed)
}

@JvmName("asLongNullableMessage")
fun Field<String?>.asLong(message: String): Field<Long?> = asLong(StringError(message))

fun Field<String>.asIsoLocalDate(error: ValidationError): Field<LocalDate> {
    val parsed = runCatching { LocalDate.parse(get()) }.getOrElse { validationError(field = name, error = error) }
    return Field(name = name, value = parsed)
}

@JvmName("asIsoLocalDateMessage")
fun Field<String>.asIsoLocalDate(message: String): Field<LocalDate> = asIsoLocalDate(StringError(message))

@JvmName("asIsoLocalDateNullable")
fun Field<String?>.asIsoLocalDate(error: ValidationError): Field<LocalDate?> {
    val current = get() ?: return Field(name = name, value = null)
    val parsed = runCatching { LocalDate.parse(current) }.getOrElse { validationError(field = name, error = error) }
    return Field(name = name, value = parsed)
}

@JvmName("asIsoLocalDateNullableMessage")
fun Field<String?>.asIsoLocalDate(message: String): Field<LocalDate?> = asIsoLocalDate(StringError(message))
