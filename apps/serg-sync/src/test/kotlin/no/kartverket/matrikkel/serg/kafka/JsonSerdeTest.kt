package no.kartverket.matrikkel.serg.kafka

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.kartverket.eksterndata.domene.Serg
import no.kartverket.eksterndata.domene.Serg.Eiernivaa
import no.kartverket.eksterndata.domene.Serg.FastEiendomSomFormuesObjektHendelse
import no.kartverket.eksterndata.domene.Serg.Identifikator
import no.kartverket.matrikkel.config.JsonSerde
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class JsonSerdeTest {

    @Test
    fun fastEiendomSomFormuesObjektHendelse_kan_serialiseres_og_deserialiseres() {
        val hendelse = FastEiendomSomFormuesObjektHendelse(
            hendelseId = "test-hendelse-id",
            matrikkelenhetId = 1L,
            skatteregistrerteEiere = setOf(
                Serg.SkatteregistrerteEier(
                    identifikator = Identifikator.Person("3"),
                    eiernivaa = Eiernivaa.EIENDOMSRETT
                ),
                Serg.SkatteregistrerteEier(
                    identifikator = Identifikator.Organisasjons("1"),
                    eiernivaa = Eiernivaa.FRAMFESTE_2
                ),
                Serg.SkatteregistrerteEier(
                    identifikator = Identifikator.AnnenPerson("2"),
                    eiernivaa = Eiernivaa.FESTE
                )
            )
        )

        val serdeFunc = JsonSerde<FastEiendomSomFormuesObjektHendelse>()
        val serialized = serdeFunc.serialize(hendelse)
        val deserialized = serdeFunc.deserialize(serialized);

        assertThat(hendelse).isEqualTo(deserialized)
    }

    @Test
    fun hendelse_kan_serialiseres_og_deserialiseres() {
        val hendelse = Hendelse (
            sekvensnummer = 1L,
            hendelseidentifikator = UUID.randomUUID(),
            skatteetatensEiendomsidentifikator = 2L,
            matrikkelUnikIdentifikator = 3L,
            registreringstidspunkt = LocalDateTime.now(),
            hendelsestype = no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype.ny,
            kommunenummer = "0301"
        )

        val serdeFunc = JsonSerde<Hendelse>()
        val serialized = serdeFunc.serialize(hendelse)
        val deserialized = serdeFunc.deserialize(serialized);

        assertThat(hendelse).isEqualTo(deserialized)
    }
}