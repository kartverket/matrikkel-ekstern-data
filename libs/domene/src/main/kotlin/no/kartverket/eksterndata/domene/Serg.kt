package no.kartverket.eksterndata.domene

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

    @Serializable
    sealed class Identifikator {
        class Person(val nr: String) : Identifikator()
        class OrgNr(val nr: String) : Identifikator()
        class AnnenPerson(val nr: String) : Identifikator()
    }

    @Serializable
    enum class Identifikatortype {
        PERSON,
        ORGANISASJON,
        ANNEN
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