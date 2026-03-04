package no.kartverket.matrikkel.serg.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eierforhold
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eieropplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FormuesobjektIdentifikator
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.util.UUID

class SergDokumentRepositoryTest : WithDatabase {
    @Test
    fun `skal kunne lagre hendelse`(): Unit = runBlocking {
        val repository = SergDokumentRepository(dataSource())

        val hendelse = Hendelse(
            sekvensnummer = 123L,
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = 1234,
            hendelsestype = Hendelsestype.ny,
            kommunenummer = "0301",
        )

        repository.upsertFraHendelse(hendelse)
        val data = repository.hentData(1234)

        assertThat(data?.matrikkelenhetId).isEqualTo(hendelse.matrikkelUnikIdentifikator)
        assertThat(data?.hendelse).isEqualTo(hendelse)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.KREVER_SYNKRONISERING)
    }

    @Test
    fun `skal kunne oppdatere innslag med formueobjekt data`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())

        val hendelse = Hendelse(
            sekvensnummer = 123L,
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = 1234,
            hendelsestype = Hendelsestype.ny,
            kommunenummer = "0301",
        )

        repository.upsertFraHendelse(hendelse)

        val formueobjekt = FastEiendomSomFormuesobjekt(
            identifikator = FormuesobjektIdentifikator(
                matrikkelUnikIdentifikator = 1234,
            ),

            hendelsesidentifikator = hendelse.hendelseidentifikator,
            eieropplysninger = listOf(
                Eieropplysninger(
                    personidentifikator = Personidentifikator(
                        organisasjonsnummer = "987987"
                    ),
                    eierforhold = Eierforhold(
                        eiernivaa = Eiernivaa.feste
                    )
                )
            )
        )

        repository.settFormueobjektdata(1234, Result.success(formueobjekt))
        3
        val data = repository.hentData(1234)

        assertThat(data?.matrikkelenhetId).isEqualTo(hendelse.matrikkelUnikIdentifikator)
        assertThat(data?.hendelse).isEqualTo(hendelse)
        assertThat(data?.formueobjekt).isEqualTo(formueobjekt)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
    }

    @Test
    fun `ny hendelse resetter status`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())

        repository.upsertFraHendelse(Hendelse(matrikkelUnikIdentifikator = 1234, hendelsestype = Hendelsestype.ny))

        var data = repository.hentData(1234)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.KREVER_SYNKRONISERING)

        repository.settFormueobjektdata(
            1234, Result.success(
                FastEiendomSomFormuesobjekt(
                    identifikator = FormuesobjektIdentifikator(
                        matrikkelUnikIdentifikator = 1234
                    )
                )
            )
        )

        data = repository.hentData(1234)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)

        repository.upsertFraHendelse(Hendelse(matrikkelUnikIdentifikator = 1234, hendelsestype = Hendelsestype.ny))

        data = repository.hentData(1234)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.KREVER_SYNKRONISERING)
    }
}