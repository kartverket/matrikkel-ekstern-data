package no.kartverket.eksterndata.domene

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

object Brreg {
    @Serializable
    enum class RegisterType {
        ENHET, UNDERENHET
    }

    @Serializable
    data class Virksomhet(
        val registerType: RegisterType,
        val organisasjonsnr: String,
        val navn: String,
        val postadresse: Adresse?,
        val forretningsadresse: Adresse?,
        val tlf: String?,
        val slettetDato: LocalDate?,
        val enhetsTypeKode: String?,
        val enhetsTypeBeskrivelse: String?
    )

    @Serializable
    data class Adresse(
        val adresse: List<String>?,
        val poststed: String?,
        val postnummer: String?,
        val kommune: String?,
        val kommunenummer: String?,
        val land: String?,
        val landkode: String?,
    )

    @Serializable
    data class Hendelse(
        val registerType: RegisterType,
        val organisasjonsnr: String,
        val oppdateringsId: Long,
        val endringstype: HendelseType,
    )

    @Serializable
    enum class HendelseType {
        UKJENT,
        NY,
        ENDRING,
        SLETTING,
        FJERNING;

        companion object {
            fun parse(value: String): HendelseType {
                return when (value) {
                    "Ukjent" -> UKJENT
                    "Ny" -> NY
                    "Endring" -> ENDRING
                    "Sletting" -> SLETTING
                    "Fjerning" -> FJERNING
                    else -> error("Ukjent hendelsetype fra BRREG: $value")
                }
            }
        }
    }
}