package no.kartverket.matrikkel.serg.formueobjekt

import Eiernivaa
import FastEiendomSomFormuesObjektHendelse
import Identifikatortype
import SkatteregistrerteEier
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype

class FastEiendomSomFormuesObjektHendelseMapper {

    fun map(hendelse: Hendelse, fastEiendomSomFormuesobjekt: FastEiendomSomFormuesobjekt): FastEiendomSomFormuesObjektHendelse{
        var skatteregistrerteEier: Set<SkatteregistrerteEier> = emptySet()

        if ((Hendelsestype.slettet != hendelse.hendelsestype) && harEierforhold(fastEiendomSomFormuesobjekt)) {
            skatteregistrerteEier = fastEiendomSomFormuesobjekt.eieropplysninger.orEmpty().map { eieropplysing ->
                val eiernivaa = eieropplysing.eierforhold?.eiernivaa?.let(::mapEiernivaa)
                    ?: throw IllegalArgumentException("Mangler eierforhold eller eiernivaa i eieropplysing")

                val ident = requireNotNull(eieropplysing.personidentifikator) {
                    "Mangler informasjon om personidentifikator i eieropplysing"
                }

                if (validerKunEnPersonidentifkator(ident)) {
                    throw IllegalArgumentException("For mange mulige personidentifiktatorer")
                }

                if ((ident.ukjentRettighetshaver ?: false)) {
                    throw IllegalArgumentException("Kan ikke opprette personident for ukjent rettighetshaver")
                }

                when {
                    ident.foedselsnummer != null -> SkatteregistrerteEier(ident.foedselsnummer!!, Identifikatortype.PERSON, eiernivaa)
                    ident.dNummer != null -> SkatteregistrerteEier(ident.dNummer!!, Identifikatortype.PERSON, eiernivaa)
                    ident.organisasjonsnummer != null -> SkatteregistrerteEier(ident.organisasjonsnummer!!, Identifikatortype.ORGANISASJON, eiernivaa)
                    ident.loepenummer != null -> SkatteregistrerteEier(ident.loepenummer!!, Identifikatortype.ANNEN, eiernivaa)
                    else -> throw IllegalArgumentException("Det mangler informasjon i personidentifikator for å kunne opprette en personidentifikator")
                }
            }.toSet()
        }

        return FastEiendomSomFormuesObjektHendelse(
            matrikkelenhetId = requireNotNull(hendelse.matrikkelUnikIdentifikator),
            skatteregistrerteEiere = skatteregistrerteEier
        )
    }

    private fun mapEiernivaa (nivaa : no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa): Eiernivaa {
        return when (nivaa) {
            no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa.eiendomsrett -> Eiernivaa.EIENDOMSRETT
            no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa.feste -> Eiernivaa.FESTE       // Matches here -> returns "Two"
            no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa.framfeste1 -> Eiernivaa.FRAMFESTE_1
            no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa.framfeste2 -> Eiernivaa.FRAMFESTE_2
            no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa.framfeste3 -> Eiernivaa.FRAMFESTE_3
        }
    }

    private fun harEierforhold(formueobjekt: FastEiendomSomFormuesobjekt): Boolean {
        return !(formueobjekt.rettighetshaverMangler ?: false)
                && (formueobjekt.eieropplysninger?: emptyList()).isNotEmpty()
    }

    private fun validerKunEnPersonidentifkator(personidentifikator: Personidentifikator): Boolean {
        val values = arrayOf(
            personidentifikator.foedselsnummer,
            personidentifikator.dNummer,
            personidentifikator.organisasjonsnummer,
            personidentifikator.loepenummer
        )
        return values.count { it != null } > 1
    }
}