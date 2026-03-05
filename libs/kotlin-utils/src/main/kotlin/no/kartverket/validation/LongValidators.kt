package no.kartverket.validation

// LONG validators
@JvmName("longInRange")
fun Field<Long>.inRange(
    range: LongRange,
    error: ValidationError,
) = refine(
    predicate = { it in range },
    error = error,
)

@JvmName("longInRangeMessage")
fun Field<Long>.inRange(
    range: LongRange,
    error: String,
) = inRange(range, StringError(error))

@JvmName("longPositive")
fun Field<Long>.positive(error: ValidationError) = inRange(1L..Long.MAX_VALUE, error)

@JvmName("longPositiveMessage")
fun Field<Long>.positive(message: String) = inRange(1L..Long.MAX_VALUE, StringError(message))

// Nullable - LONG validators

@JvmName("nullableLongInRange")
fun Field<Long?>.inRange(
    range: LongRange,
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

@JvmName("nullableLongInRangeMessage")
fun Field<Long?>.inRange(
    range: LongRange,
    error: String,
) = inRange(range, StringError(error))

@JvmName("nullableLongPositive")
fun Field<Long?>.positive(error: ValidationError) = inRange(1L..Long.MAX_VALUE, error)

@JvmName("nullableLongPositiveMessage")
fun Field<Long?>.positive(message: String) = inRange(1L..Long.MAX_VALUE, StringError(message))
