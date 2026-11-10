package no.kartverket.eksterndata.domene

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

object Brreg {
    @Serializable
    data class VirksomhetHendelse(
        val organisasjonsnr: String,
        val navn: String,
        val postadresse: Adresse.Postadresse? = null,
        val forretningsadresse: Adresse.ForretningAdresse? = null,
        val tlf: String? = null,
        val slettetDato: Instant? = null,
        val enhetsTypeKode: String? = null,
        val enhetsTypeBeskrivelse: String? = null
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
    sealed class Adresse {
        abstract val adresselinje1: String?
        abstract val adresselinje2: String?
        abstract val adresselinje3: String?
        abstract val postnr: String?
        abstract val poststed: String?
        abstract val land: String?

        @Serializable
        @SerialName("Postadresse")
        data class Postadresse(
            override val adresselinje1: String? = null,
            override val adresselinje2: String? = null,
            override val adresselinje3: String? = null,
            override val postnr: String? = null,
            override val poststed: String? = null,
            override val land: String? = null,
            val utenlandskPoststed: String? = null
        ) : Adresse()

        @Serializable
        @SerialName("ForretningAdresse")
        data class ForretningAdresse(
            override val adresselinje1: String? = null,
            override val adresselinje2: String? = null,
            override val adresselinje3: String? = null,
            override val postnr: String? = null,
            override val poststed: String? = null,
            override val land: String? = null,
            val utenlandskAdresse: String? = null
        ) : Adresse()
    }
}