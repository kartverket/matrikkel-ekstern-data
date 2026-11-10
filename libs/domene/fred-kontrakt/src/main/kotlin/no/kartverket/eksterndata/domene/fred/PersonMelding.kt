package no.kartverket.eksterndata.domene.fred

import kotlinx.serialization.Serializable

@Serializable
data class PersonMelding(
    val pid: String,
    val pidType: Int, // beholdes hvis Kafka faktisk sender et tall
    val etternavn: String,
    val fornavn: String,
    val fulltNavn: String,
    val status: String,
    val mellomnavn: String = "",
    val postadresse: Postadresse? = null,
    val bostedsadresse: Bostedsadresse? = null,
    val spesifisertRegisterkode: Int? = null,
    val hjemlandsadresse: Hjemlandsadresse? = null,
) {
    @Serializable
    data class Postadresse(
        val adresseLinje1: String,
        val adresseLinje2: String,
        val adresseLinje3: String,
        val postnr: Int,
        val landKode: Int,
    )

    @Serializable
    data class Bostedsadresse(
        val gatenr: Long,
        val husnr: Int,
        val husbokstav: String,
        val gardsnr: Long,
        val bruksnr: Long,
        val festenr: Long,
        val undernr: Long,
        val bolignr: String,
        val kommunenr: Int,
        val postnr: Int,
    )

    @Serializable
    data class Hjemlandsadresse(
        val hjemlandsadresseLinje1: String,
        val hjemlandsadresseLinje2: String,
        val hjemlandsadresseLinje3: String,
        val hjemlandsLandKode: Int?,
    )
}
