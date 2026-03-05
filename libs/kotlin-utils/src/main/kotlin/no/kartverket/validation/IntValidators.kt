package no.kartverket.validation

// INT validators
@JvmName("intInRange")
fun Field<Int>.inRange(
    range: IntRange,
    error: ValidationError,
) = refine(
    predicate = { it in range },
    error = error,
)

@JvmName("intInRangeMessage")
fun Field<Int>.inRange(
    range: IntRange,
    error: String,
) = inRange(range, StringError(error))

@JvmName("intPositive")
fun Field<Int>.positive(error: ValidationError) = inRange(1..Int.MAX_VALUE, error)

@JvmName("intPositiveMessage")
fun Field<Int>.positive(message: String) = inRange(1..Int.MAX_VALUE, StringError(message))

// Nullable- INT validators

@JvmName("nullableIntInRange")
fun Field<Int?>.inRange(
    range: IntRange,
    error: ValidationError,
) = refine(
    predicate = {
        if (it == null) {
            true // Nulls are always in range
        } else {
            it in range
        }
    },
    error = error,
)

@JvmName("nullableIntInRangeMessage")
fun Field<Int?>.inRange(
    range: IntRange,
    error: String,
) = inRange(range, StringError(error))

@JvmName("nullableIntPositive")
fun Field<Int?>.positive(error: ValidationError) = inRange(1..Int.MAX_VALUE, error)

@JvmName("nullableIntPositiveMessage")
fun Field<Int?>.positive(message: String) = inRange(1..Int.MAX_VALUE, StringError(message))
