package no.kartverket.eksterndata.domene

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

    @Serializable
    sealed class Identifikator {
        @Serializable
        @SerialName("Person")
        data class Person(val nr: String) : Identifikator()
        @Serializable
        @SerialName("Organisasjons")
        data class Organisasjons(val nr: String) : Identifikator()
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