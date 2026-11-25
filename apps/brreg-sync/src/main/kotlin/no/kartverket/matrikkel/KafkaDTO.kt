package no.kartverket.matrikkel

import kotlinx.datetime.LocalDate
import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.tjenestespesifikasjoner.brreg.models.*

fun Enhet.toKafkaDTO(): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.ENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = this.postadresse?.toKafkaDTO(),
        forretningsadresse = this.forretningsadresse?.toKafkaDTO(),
        tlf = this.telefon ?: this.mobil,
        slettetDato = null,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

fun SlettetEnhet.toKafkaDTO(slettetDato: LocalDate): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.ENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = null,
        forretningsadresse = null,
        tlf = null,
        slettetDato = slettetDato,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

fun Underenhet.toKafkaDTO(): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.UNDERENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = this.postadresse?.toKafkaDTO(),
        forretningsadresse = null,
        tlf = this.telefon ?: this.mobil,
        slettetDato = null,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

fun SlettetUnderEnhet.toKafkaDTO(slettetDato: LocalDate): Brreg.Virksomhet {
    return Brreg.Virksomhet(
        registerType = Brreg.RegisterType.UNDERENHET,
        organisasjonsnr = this.organisasjonsnummer,
        navn = this.navn,
        postadresse = null,
        forretningsadresse = null,
        tlf = null,
        slettetDato = slettetDato,
        enhetsTypeKode = this.organisasjonsform.kode,
        enhetsTypeBeskrivelse = this.organisasjonsform.beskrivelse,
    )
}

// Adresse-typene fra OAS er forskjellige (men like) objekter. Trenger derfor separate mappere
private fun EnhetPostadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}

private fun UnderenhetPostadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}

private fun EnhetForretningsadresse.toKafkaDTO(): Brreg.Adresse {
    return Brreg.Adresse(
        adresse = this.adresse,
        poststed = this.poststed,
        postnummer = this.postnummer,
        kommune = this.kommune,
        kommunenummer = this.kommunenummer,
        land = this.land,
        landkode = this.landkode,
    )
}

fun OppdateringerEnhet.toKafkaDTO(): Brreg.Hendelse {
    return Brreg.Hendelse(
        registerType = Brreg.RegisterType.ENHET,
        oppdateringsId = this.oppdateringsid.toLong(),
        organisasjonsnr = this.organisasjonsnummer,
        endringstype = Brreg.HendelseType.parse(this.endringstype)
    )
}

fun OppdateringerUnderenhet.toKafkaDTO(): Brreg.Hendelse {
    return Brreg.Hendelse(
        registerType = Brreg.RegisterType.UNDERENHET,
        oppdateringsId = this.oppdateringsid.toLong(),
        organisasjonsnr = this.organisasjonsnummer,
        endringstype = Brreg.HendelseType.parse(this.endringstype)
    )
}