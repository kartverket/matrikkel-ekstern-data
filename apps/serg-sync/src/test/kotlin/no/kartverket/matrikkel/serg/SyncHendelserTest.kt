package no.kartverket.matrikkel.serg

import assertk.assertThat
import assertk.assertions.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentStatus
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelser
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.util.*

class SyncHendelserTest : WithDatabase {
    @Test
    fun `start fra 1 om sekvensnumemr ikke er satt`() = runBlocking {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.delete("sekvensnummer")

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }
    }

    @Test
    fun `plukker opp sekvensnummer om satt`() = runBlocking {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "123")

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(123L, 1000, any())
        }
    }

    @Test
    fun `feil ved henting av sekvensnummer rapporteres`() = runBlocking {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "ikke_tall")

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result)
            .isFailure()
            .isInstanceOf(NumberFormatException::class)

        verify(exactly = 0) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any())
        }
    }

    @Test
    fun `henter hendelser fra SERG`() = runBlocking {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "1")
        val documentRepository = SergDocumentRepository(dataSource())
        val hendelser = listOf(
            hendelse(id = 1001L, type = Hendelsestype.ny, seq = 10L),
            hendelse(id = 1002L, type = Hendelsestype.slettet, seq = 11L),
        )
        val hendelserApi = gittHendelseApiSomReturnerer(hendelser)

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result)
            .isSuccess()
            .isEqualTo(hendelser)

        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }

        val nyHendelseData = documentRepository.getData(1001L)
        assertThat(nyHendelseData?.hendelse).isEqualTo(hendelser[0])
        assertThat(nyHendelseData?.status).isEqualTo(SergDocumentStatus.REQUIRE_SYNCHRONIZATION)

        val slettetHendelseData = documentRepository.getData(1002L)
        assertThat(slettetHendelseData?.hendelse).isEqualTo(hendelser[1])
        assertThat(slettetHendelseData?.status).isEqualTo(SergDocumentStatus.DELETED)

        assertThat(keyValueRepository.getValue("sekvensnummer")).isEqualTo("11")
    }

    @Test
    fun `feil ved henting av hendelser rapporteres`() = runBlocking {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "123")
        val hendelserApi = mockk<HendelserApi>()
        every {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any())
        } throws RuntimeException("SERG unavailable")

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result)
            .isFailure()
            .isInstanceOf(RuntimeException::class)
            .hasMessage("SERG unavailable")

        verify(exactly = 3) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(123L, 1000, any())
        }
        assertThat(keyValueRepository.getValue("sekvensnummer")).isEqualTo("123")
    }

    @Test
    fun `lagrer alle hendelser og rapporterer om eventuelle ugyldige data`() = runBlocking {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "1")
        val documentRepository = SergDocumentRepository(dataSource())
        val hendelserApi = gittHendelseApiSomReturnerer(
            listOf(
                hendelse(id = 2001L, type = Hendelsestype.ny, seq = 12L),
                hendelse(id = null, type = Hendelsestype.endret, seq = 13L), // Invalid, missing ID
                hendelse(id = 2002L, type = Hendelsestype.slettet, seq = 14L)
            )
        )

        val result = SyncHendelser(dataSource(), hendelserApi).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }
        assertThat(documentRepository.getData(2001L)).isNotNull()
        assertThat(documentRepository.getData(2002L)).isNotNull()
    }

    private fun hendelse(id: Long?, type: Hendelsestype, seq: Long = 1L): Hendelse {
        return Hendelse(
            sekvensnummer = seq,
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = id,
            hendelsestype = type,
            kommunenummer = "0301",
        )
    }

    private fun gittHendelseApiSomReturnerer(list: List<Hendelse>): HendelserApi {
        val hendelserApi = mockk<HendelserApi>()
        every {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any())
        } returns Hendelser(hendelser = list)
        return hendelserApi
    }
}
