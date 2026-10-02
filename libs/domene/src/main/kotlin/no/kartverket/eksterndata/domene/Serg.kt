package no.kartverket.eksterndata.domene

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Serg {

    @Serializable
    data class FastEiendomSomFormuesObjektHendelse(
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
        JsonSubTypes.Type(value = Identifikator.Person::class, name = "PERSON"),
        JsonSubTypes.Type(value = Identifikator.OrgNr::class, name = "ORGANISASJON"),
        JsonSubTypes.Type(value = Identifikator.AnnenPerson::class, name = "ANNEN")
    )
    @Serializable
    sealed class Identifikator {
        @Serializable
        @SerialName("Person")
        class Person(val nr: String) : Identifikator()
        @Serializable
        @SerialName("OrgNr")
        class OrgNr(val nr: String) : Identifikator()
        @Serializable
        @SerialName("AnnenPerson")
        class AnnenPerson(val nr: String) : Identifikator()
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