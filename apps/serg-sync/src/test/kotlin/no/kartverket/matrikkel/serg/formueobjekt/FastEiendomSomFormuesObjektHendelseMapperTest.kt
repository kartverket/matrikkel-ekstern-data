package no.kartverket.matrikkel.serg.formueobjekt

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.kartverket.eksterndata.domene.Serg
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eierforhold
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eieropplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class FastEiendomSomFormuesObjektHendelseMapperTest {
    private val mapper = FastEiendomSomFormuesObjektHendelseMapper()

    @Test
    fun `mapper registrert eier når rettighetshaver finnes`() {
        val hendelseId = UUID.randomUUID()
        val hendelse = Hendelse(
            hendelseidentifikator = hendelseId,
            matrikkelUnikIdentifikator = 123L,
            hendelsestype = Hendelsestype.ny,
        )
        val formuesobjekt = FastEiendomSomFormuesobjekt(
            rettighetshaverMangler = false,
            eieropplysninger = listOf(
                Eieropplysninger(
                    personidentifikator = Personidentifikator(
                        foedselsnummer = "01019012345",
                    ),
                    eierforhold = Eierforhold(
                        eiernivaa = Eiernivaa.eiendomsrett,
                    ),
                ),
            ),
        )

        val resultat = mapper.map(hendelse, formuesobjekt)

        assertThat(resultat).isEqualTo(
            Serg.FastEiendomSomFormuesObjektHendelse(
                hendelseId = hendelseId.toString(),
                matrikkelenhetId = 123L,
                skatteregistrerteEiere = setOf(
                    Serg.SkatteregistrerteEier(
                        identifikator = Serg.Identifikator.Person("01019012345"),
                        eiernivaa = Serg.Eiernivaa.EIENDOMSRETT,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `slettet hendelse gir tom eierliste selv om formuesobjektet har eiere`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                foedselsnummer = "01019012345",
            ),
            hendelsestype = Hendelsestype.slettet,
        )

        assertThat(resultat).isEqualTo(emptySet())
    }

    @Test
    fun `rettighetshaver mangler gir ingen skatteregistrerte eiere`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                foedselsnummer = "01019012345",
            ),
            rettighetshaverMangler = true,
        )

        assertThat(resultat).isEqualTo(emptySet())
    }

    @Test
    fun `endret hendelse mapper organisasjonsnummer og feste`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                organisasjonsnummer = "987654321",
            ),
            eiernivaa = Eiernivaa.feste,
            hendelsestype = Hendelsestype.endret,
        )

        assertThat(resultat).isEqualTo(
            setOf(
                Serg.SkatteregistrerteEier(
                    identifikator =
                        Serg.Identifikator.Organisasjons("987654321"),
                    eiernivaa = Serg.Eiernivaa.FESTE,
                ),
            ),
        )
    }

    @Test
    fun `D-nummer mappes til person`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                dNummer = "41019012345",
            ),
        )

        assertThat(resultat).isEqualTo(
            setOf(
                Serg.SkatteregistrerteEier(
                    identifikator =
                        Serg.Identifikator.Person("41019012345"),
                    eiernivaa = Serg.Eiernivaa.EIENDOMSRETT,
                ),
            ),
        )
    }

    @Test
    fun `løpenummer mappes til annen person`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                loepenummer = "LOEPENR-123",
            ),
        )

        assertThat(resultat).isEqualTo(
            setOf(
                Serg.SkatteregistrerteEier(
                    identifikator =
                        Serg.Identifikator.AnnenPerson("LOEPENR-123"),
                    eiernivaa = Serg.Eiernivaa.EIENDOMSRETT,
                ),
            ),
        )
    }

    @Test
    fun `ukjent rettighetshaver utelates`() {
        val resultat = mapEiere(
            personidentifikator = Personidentifikator(
                ukjentRettighetshaver = true,
            ),
        )

        assertThat(resultat).isEqualTo(emptySet())
    }

    @Test
    fun `flere identifikatorer på samme eier avvises`() {
        assertThrows<IllegalArgumentException> {
            mapEiere(
                personidentifikator = Personidentifikator(
                    foedselsnummer = "01019012345",
                    dNummer = "41019012345",
                ),
            )
        }
    }

    private fun mapEiere(
        personidentifikator: Personidentifikator,
        eiernivaa: Eiernivaa = Eiernivaa.eiendomsrett,
        hendelsestype: Hendelsestype = Hendelsestype.ny,
        rettighetshaverMangler: Boolean = false,
    ): Set<Serg.SkatteregistrerteEier> {
        val hendelse = Hendelse(
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = 123L,
            hendelsestype = hendelsestype,
        )

        val formuesobjekt = FastEiendomSomFormuesobjekt(
            rettighetshaverMangler = rettighetshaverMangler,
            eieropplysninger = listOf(
                Eieropplysninger(
                    personidentifikator = personidentifikator,
                    eierforhold = Eierforhold(
                        eiernivaa = eiernivaa,
                    ),
                ),
            ),
        )

        return mapper
            .map(hendelse, formuesobjekt)
            .skatteregistrerteEiere
    }
}