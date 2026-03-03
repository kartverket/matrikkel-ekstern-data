package no.kartverket.serg.mock

import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Andel
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Bruksenhet
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Bygning
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiendomsopplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eierforhold
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eieropplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FormuesobjektIdentifikator
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Formuesopplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Matrikkelnummer
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Tinglysing
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class FakeDataGenerator {
    fun generate(
        sequence: Long,
        preset: PresetName,
    ): GeneratedRow {
        val eventDate = LocalDate.of(2022, 1, 1).plusDays((sequence - 1).coerceAtLeast(0))
        val eventType = eventTypeFor(sequence, preset)
        val eventId = UUID.nameUUIDFromBytes("serg-mock-hendelse-$sequence".toByteArray(StandardCharsets.UTF_8))
        val skatteetatensEiendomsidentifikator = 700_000_000_000L + sequence
        val matrikkelUnikIdentifikator = 900_000_000_000L + sequence
        val kommunenummer = "%04d".format(3001 + (sequence % 350).toInt())
        val registreringstidspunkt =
            OffsetDateTime.of(
                eventDate.year,
                eventDate.monthValue,
                eventDate.dayOfMonth,
                ((sequence * 7) % 24).toInt(),
                ((sequence * 13) % 60).toInt(),
                ((sequence * 17) % 60).toInt(),
                0,
                ZoneOffset.UTC,
            )

        val hendelse =
            Hendelse(
                sekvensnummer = sequence,
                hendelseidentifikator = eventId,
                skatteetatensEiendomsidentifikator = skatteetatensEiendomsidentifikator,
                matrikkelUnikIdentifikator = matrikkelUnikIdentifikator,
                registreringstidspunkt = registreringstidspunkt,
                hendelsestype = eventType,
                kommunenummer = kommunenummer,
            )

        val formuesobjekt =
            FastEiendomSomFormuesobjekt(
                identifikator =
                    FormuesobjektIdentifikator(
                        skatteetatensEiendomsidentifikator = skatteetatensEiendomsidentifikator,
                        matrikkelUnikIdentifikator = matrikkelUnikIdentifikator,
                    ),
                hendelsesidentifikator = eventId,
                rettighetshaverMangler = false,
                eiendomsopplysninger =
                    Eiendomsopplysninger(
                        matrikkelnummer =
                            Matrikkelnummer(
                                kommunenummer = kommunenummer,
                                gaardsnummer = 1 + (sequence % 500),
                                bruksnummer = 1 + (sequence % 1000),
                                festenummer = null,
                                seksjonsnummer = if (sequence % 2L == 0L) sequence % 50 else null,
                            ),
                        bygning =
                            listOf(
                                Bygning(
                                    bygningsnummer = "B-${1_000_000 + sequence}",
                                    matrikkelUnikIdentifikator = matrikkelUnikIdentifikator,
                                    bruksenheter =
                                        listOf(
                                            Bruksenhet(
                                                bruksenhetsnummer = "H%04d".format((sequence % 9999).toInt() + 1),
                                                matrikkelUnikIdentifikator = matrikkelUnikIdentifikator,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                formuesopplysninger =
                    Formuesopplysninger(
                        formuestype = formuestypeFor(sequence),
                    ),
                eieropplysninger =
                    listOf(
                        Eieropplysninger(
                            personidentifikator =
                                Personidentifikator(
                                    foedselsnummer = syntheticFoedselsnummer(sequence),
                                ),
                            erTinglyst = true,
                            eierforhold =
                                Eierforhold(
                                    tinglysing =
                                        Tinglysing(
                                            overdragelsesdato = eventDate.minusDays((sequence % 30) + 1),
                                            tinglysningsdato = eventDate,
                                            omsetningstype = "kjop",
                                        ),
                                    eierandel = Andel(teller = 1, nevner = 1),
                                    eiernivaa = Eiernivaa.eiendomsrett,
                                ),
                        ),
                    ),
            )

        return GeneratedRow(hendelse = hendelse, formuesobjekt = formuesobjekt, eventDate = eventDate)
    }

    fun eventTypeFor(
        sequence: Long,
        preset: PresetName,
    ): Hendelsestype {
        val bucket = (sequence % 10).toInt()
        return when (preset) {
            PresetName.BALANCED ->
                when ((sequence % 3).toInt()) {
                    0 -> Hendelsestype.slettet
                    1 -> Hendelsestype.ny
                    else -> Hendelsestype.endret
                }

            PresetName.NEW_HEAVY ->
                when {
                    bucket < 7 -> Hendelsestype.ny
                    bucket < 9 -> Hendelsestype.endret
                    else -> Hendelsestype.slettet
                }

            PresetName.DELETE_HEAVY ->
                when {
                    bucket < 1 -> Hendelsestype.ny
                    bucket < 4 -> Hendelsestype.endret
                    else -> Hendelsestype.slettet
                }
        }
    }

    private fun formuestypeFor(sequence: Long): String =
        when ((sequence % 3).toInt()) {
            0 -> "primaerbolig"
            1 -> "sekundaerbolig"
            else -> "naeringseiendom"
        }

    private fun syntheticFoedselsnummer(sequence: Long): String {
        val suffix = (10_000 + (sequence % 90_000)).toInt().toString().padStart(5, '0')
        return "010190$suffix"
    }
}
