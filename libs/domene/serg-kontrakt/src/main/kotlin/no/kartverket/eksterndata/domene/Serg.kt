package no.kartverket.eksterndata.domene

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Serg {

    @Serializable
    data class FastEiendomSomFormuesObjektHendelse(
        val hendelseId: String,
        val matrikkelenhetId: Long,
        val skatteregistrerteEiere: Set<SkatteregistrerteEier>
    )

    @Serializable
    data class SkatteregistrerteEier (
        val identifikator: Identifikator,
        val eiernivaa: Eiernivaa
    )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = Identifikator.Person::class, name = "Person"),
        JsonSubTypes.Type(value = Identifikator.Organisasjon::class, name = "Organisasjon"),
        JsonSubTypes.Type(value = Identifikator.AnnenPerson::class, name = "AnnenPerson")
    )
    @Serializable
    sealed class Identifikator {
        @Serializable
        @SerialName("Person")
        data class Person(val nr: String) : Identifikator()
        @Serializable
        @SerialName("Organisasjon")
        data class Organisasjon(val nr: String) : Identifikator()
        @Serializable
        @SerialName("AnnenPerson")
        data class AnnenPerson(val nr: String) : Identifikator()
    }

    @Serializable
    enum class Eiernivaa {
        EIENDOMSRETT,
        FESTE,
        FRAMFESTE_1,
        FRAMFESTE_2,
        FRAMFESTE_3
    }
}