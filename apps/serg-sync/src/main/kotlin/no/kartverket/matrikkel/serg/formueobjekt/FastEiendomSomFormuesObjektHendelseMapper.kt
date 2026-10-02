package no.kartverket.matrikkel.serg.formueobjekt

import no.kartverket.eksterndata.domene.Serg.Eiernivaa;
import no.kartverket.eksterndata.domene.Serg.FastEiendomSomFormuesObjektHendelse
import no.kartverket.eksterndata.domene.Serg.Identifikator
import no.kartverket.eksterndata.domene.Serg.SkatteregistrerteEier
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eieropplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa as SergEiernivaa

class FastEiendomSomFormuesObjektHendelseMapper {

    fun map(hendelse: Hendelse, fastEiendomSomFormuesobjekt: FastEiendomSomFormuesobjekt): FastEiendomSomFormuesObjektHendelse {
        val eiere: List<Eieropplysninger> = when {
            fastEiendomSomFormuesobjekt.rettighetshaverMangler == false -> emptyList()
            hendelse.hendelsestype == Hendelsestype.ny -> fastEiendomSomFormuesobjekt.eieropplysninger.orEmpty()
            hendelse.hendelsestype == Hendelsestype.endret -> fastEiendomSomFormuesobjekt.eieropplysninger.orEmpty()
            hendelse.hendelsestype == Hendelsestype.slettet -> emptyList()
            else -> emptyList()
        }

        return FastEiendomSomFormuesObjektHendelse(
            hendelseId = requireNotNull(hendelse.hendelseidentifikator).toString(),
            matrikkelenhetId = requireNotNull(hendelse.matrikkelUnikIdentifikator),
            skatteregistrerteEiere = eiere.mapNotNull(::skatteEiere).toSet(),
        )
    }

    private fun skatteEiere(eieropplysing: Eieropplysninger): SkatteregistrerteEier? {
        val ident = requireNotNull(eieropplysing.personidentifikator) {
            "Mangler informasjon om personidentifikator i eieropplysing"
        }

        if (ident.ukjentRettighetshaver ?: false) {
            return null
        }

        require(validerKunEnPersonidentifikator(ident)) {
            "For mange mulige personidentifiktatorer"
        }

        val eiernivaa = requireNotNull(eieropplysing.eierforhold?.eiernivaa?.let(::mapEiernivaa)) {
            "Mangler eierforhold eller eiernivaa i eieropplysing"
        }

        return SkatteregistrerteEier(
            identifikator = mapIdentifikator(ident),
            eiernivaa = eiernivaa
        )
    }

    private fun mapIdentifikator(personidentifikator: Personidentifikator): Identifikator {
        return when {
            personidentifikator.foedselsnummer != null -> Identifikator.Person(requireNotNull(personidentifikator.foedselsnummer))
            personidentifikator.dNummer != null -> Identifikator.Person(requireNotNull(personidentifikator.dNummer))
            personidentifikator.organisasjonsnummer != null -> Identifikator.Organisasjons(requireNotNull(personidentifikator.organisasjonsnummer))
            personidentifikator.loepenummer != null -> Identifikator.AnnenPerson(requireNotNull(personidentifikator.loepenummer))
            else -> throw IllegalArgumentException("Det mangler informasjon i personidentifikator for å kunne opprette en personidentifikator")
        }
    }

    private fun mapEiernivaa(nivaa: SergEiernivaa): Eiernivaa {
        return when (nivaa) {
            SergEiernivaa.eiendomsrett -> Eiernivaa.EIENDOMSRETT
            SergEiernivaa.feste -> Eiernivaa.FESTE
            SergEiernivaa.framfeste1 -> Eiernivaa.FRAMFESTE_1
            SergEiernivaa.framfeste2 -> Eiernivaa.FRAMFESTE_2
            SergEiernivaa.framfeste3 -> Eiernivaa.FRAMFESTE_3
        }
    }

    private fun validerKunEnPersonidentifikator(personidentifikator: Personidentifikator): Boolean {
        val values = arrayOf(
            personidentifikator.foedselsnummer,
            personidentifikator.dNummer,
            personidentifikator.organisasjonsnummer,
            personidentifikator.loepenummer
        )
        return values.count { it != null } == 1
    }
}