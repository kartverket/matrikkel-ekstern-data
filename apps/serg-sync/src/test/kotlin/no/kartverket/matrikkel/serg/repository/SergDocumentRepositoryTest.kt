package no.kartverket.matrikkel.serg.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.*
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.util.*

class SergDocumentRepositoryTest : WithDatabase {
    @Test
    fun `skal kunne lagre hendelse`(): Unit = runBlocking {
        val repository = SergDocumentRepository(dataSource())

        val hendelse = Hendelse(
            sekvensnummer = 123L,
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = 1234,
            hendelsestype = Hendelsestype.ny,
            kommunenummer = "0301",
        )

        repository.upsertFraHendelse(hendelse)
        val data = repository.getData(1234)

        assertThat(data?.matrikkelenhetId).isEqualTo(hendelse.matrikkelUnikIdentifikator)
        assertThat(data?.hendelse).isEqualTo(hendelse)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.REQUIRE_SYNCHRONIZATION)
    }

    @Test
    fun `skal kunne oppdatere innslag med formueobjekt data`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())

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
        val data = repository.getData(1234)

        assertThat(data?.matrikkelenhetId).isEqualTo(hendelse.matrikkelUnikIdentifikator)
        assertThat(data?.hendelse).isEqualTo(hendelse)
        assertThat(data?.formueobjekt).isEqualTo(formueobjekt)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
    }

    @Test
    fun `ny hendelse resetter status`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())

        repository.upsertFraHendelse(Hendelse(matrikkelUnikIdentifikator = 1234, hendelsestype = Hendelsestype.ny))

        var data = repository.getData(1234)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.REQUIRE_SYNCHRONIZATION)

        repository.settFormueobjektdata(
            1234, Result.success(
                FastEiendomSomFormuesobjekt(
                    identifikator = FormuesobjektIdentifikator(
                        matrikkelUnikIdentifikator = 1234
                    )
                )
            )
        )

        data = repository.getData(1234)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)

        repository.upsertFraHendelse(Hendelse(matrikkelUnikIdentifikator = 1234, hendelsestype = Hendelsestype.ny))

        data = repository.getData(1234)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.REQUIRE_SYNCHRONIZATION)
    }
}