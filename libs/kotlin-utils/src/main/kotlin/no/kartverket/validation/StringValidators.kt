package no.kartverket.validation

fun Field<String>.nonBlank(error: ValidationError) =
    refine(
        predicate = { it.isNotBlank() },
        error = error,
    )

@JvmName("nonBlankMessage")
fun Field<String>.nonBlank(error: String) = nonBlank(StringError(error))
