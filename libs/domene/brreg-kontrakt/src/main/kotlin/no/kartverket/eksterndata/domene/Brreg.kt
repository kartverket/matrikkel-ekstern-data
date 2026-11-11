package no.kartverket.eksterndata.domene

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate

object Brreg {
    @Serializable
    data class VirksomhetHendelse(
        val organisasjonsnr: String,
        val navn: String,
        val postadresse: Adresse.Postadresse?,
        val forretningsadresse: Adresse.ForretningAdresse?,
        val tlf: String?,
        val slettetDato: LocalDate?,
        val enhetsTypeKode: String?,
        val enhetsTypeBeskrivelse: String?
    )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = Adresse.Postadresse::class, name = "Postadresse"),
        JsonSubTypes.Type(value = Adresse.ForretningAdresse::class, name = "ForretningAdresse")
    )
    @Serializable
    sealed interface Adresse {
        val adresselinje1: String?
        val adresselinje2: String?
        val adresselinje3: String?
        val postnr: String?
        val poststed: String?
        val land: String?

        @Serializable
        @SerialName("Postadresse")
        data class Postadresse(
            override val adresselinje1: String?,
            override val adresselinje2: String?,
            override val adresselinje3: String?,
            override val postnr: String,
            override val poststed: String,
            override val land: String?,
            val utenlandskPoststed: String?
        ) : Adresse

        @Serializable
        @SerialName("ForretningAdresse")
        data class ForretningAdresse(
            override val adresselinje1: String?,
            override val adresselinje2: String?,
            override val adresselinje3: String?,
            override val postnr: String?,
            override val poststed: String?,
            override val land: String?,
            val utenlandskAdresse: String?
        ) : Adresse
    }
}