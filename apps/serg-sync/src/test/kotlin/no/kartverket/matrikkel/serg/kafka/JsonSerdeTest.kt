package no.kartverket.matrikkel.serg.kafka

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
        val deserialized = serdeFunc.deserialize(serialized)

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
        val deserialized = serdeFunc.deserialize(serialized)

        assertThat(hendelse).isEqualTo(deserialized)
    }

    @Test
    fun `formuesobjekt serialiseres med forventet Kafka-kontrakt`() {
        val melding = FastEiendomSomFormuesObjektHendelse(
            hendelseId = "hendelse-1",
            matrikkelenhetId = 123L,
            skatteregistrerteEiere = setOf(
                Serg.SkatteregistrerteEier(
                    identifikator = Identifikator.Person("01019012345"),
                    eiernivaa = Eiernivaa.EIENDOMSRETT,
                ),
            ),
        )

        val serialisert = JsonSerde<FastEiendomSomFormuesObjektHendelse>()
            .serialize(melding)

        val objectMapper = jacksonObjectMapper()
        val faktiskJson = objectMapper.readTree(serialisert)

        val forventetJson = objectMapper.readTree(
            """
        {
          "hendelseId": "hendelse-1",
          "matrikkelenhetId": 123,
          "skatteregistrerteEiere": [
            {
              "identifikator": {
                "type": "Person",
                "nr": "01019012345"
              },
              "eiernivaa": "EIENDOMSRETT"
            }
          ]
        }
        """.trimIndent(),
        )

        assertThat(faktiskJson).isEqualTo(forventetJson)
    }

    @Test
    fun `organisasjonsidentifikator serialiseres med forventet JSON-kontrakt`() {
        verifiserIdentifikatorKontrakt(
            identifikator = Identifikator.Organisasjons("987654321"),
            forventetType = "Organisasjons",
            forventetNummer = "987654321",
        )
    }

    @Test
    fun `annen person serialiseres med forventet JSON-kontrakt`() {
        verifiserIdentifikatorKontrakt(
            identifikator = Identifikator.AnnenPerson("LOEPENR-123"),
            forventetType = "AnnenPerson",
            forventetNummer = "LOEPENR-123",
        )
    }

    private fun verifiserIdentifikatorKontrakt(
        identifikator: Identifikator,
        forventetType: String,
        forventetNummer: String,
    ) {
        val melding = FastEiendomSomFormuesObjektHendelse(
            hendelseId = "hendelse-1",
            matrikkelenhetId = 123L,
            skatteregistrerteEiere = setOf(
                Serg.SkatteregistrerteEier(
                    identifikator = identifikator,
                    eiernivaa = Eiernivaa.EIENDOMSRETT,
                ),
            ),
        )

        val serialisert =
            JsonSerde<FastEiendomSomFormuesObjektHendelse>()
                .serialize(melding)

        val objectMapper = jacksonObjectMapper()
        val faktiskJson = objectMapper.readTree(serialisert)

        val faktiskIdentifikator =
            faktiskJson["skatteregistrerteEiere"][0]["identifikator"]

        val forventetIdentifikator = objectMapper.readTree(
            """
        {
          "type": "$forventetType",
          "nr": "$forventetNummer"
        }
        """.trimIndent(),
        )

        assertThat(faktiskIdentifikator)
            .isEqualTo(forventetIdentifikator)
    }
}