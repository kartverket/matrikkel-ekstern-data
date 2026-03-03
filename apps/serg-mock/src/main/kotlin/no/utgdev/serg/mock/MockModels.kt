package no.utgdev.serg.mock

import kotlinx.serialization.Serializable

@Serializable
enum class PresetName {
    BALANCED,
    NEW_HEAVY,
    DELETE_HEAVY,
}

@Serializable
data class GenerateRequest(
    val count: Int,
    val preset: PresetName = PresetName.BALANCED,
)

@Serializable
data class GenerateResponse(
    val generated: Int,
    val totalEvents: Int,
    val firstSeq: Long?,
    val lastSeq: Long?,
)

@Serializable
data class StateResponse(
    val totalEvents: Int,
    val nextSequence: Long,
    val presets: List<String>,
    val recent: List<RecentEventView>,
    val firstSeq: Long?,
    val lastSeq: Long?,
)

@Serializable
data class RecentEventView(
    val sekvensnummer: Long,
    val hendelseidentifikator: String,
    val hendelsestype: String,
    val registreringstidspunkt: String,
    val kommunenummer: String?,
)

@Serializable
data class ClearResponse(
    val clearedEvents: Int,
    val clearedObjects: Int,
)

@Serializable
internal data class AdminErrorResponse(
    val message: String,
)

internal data class GeneratedRow(
    val hendelse: no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelse,
    val formuesobjekt: no.utgdev.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt,
    val eventDate: java.time.LocalDate,
)

internal data class StoreSnapshot(
    val totalEvents: Int,
    val nextSequence: Long,
    val firstSeq: Long?,
    val lastSeq: Long?,
    val recent: List<RecentEventView>,
)

internal data class GenerateSummary(
    val generated: Int,
    val totalEvents: Int,
    val firstSeq: Long?,
    val lastSeq: Long?,
)

internal data class ClearSummary(
    val clearedEvents: Int,
    val clearedObjects: Int,
)
