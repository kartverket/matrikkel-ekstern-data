package no.kartverket.matrikkel.serg.formueobjekt

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSuccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FormuesobjektIdentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class FormueobjektSyncServiceTest : WithDatabase {
    private var nesteSekvensnummer = 0L

    @Test
    fun `ingen dokumenter å synkronisere`() = runBlocking {
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
    }

    @Test
    fun `henter kun KREVER_SYNKRONISERING`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val requireId = 1001L
        val syncedId = 1002L
        val deletedId = 1003L
        val failureId = 1004L
        val requireHendelseId = upsertHendelse(repository, requireId)
        val syncedHendelseId = upsertHendelse(repository, syncedId)
        upsertHendelse(repository, deletedId, type = Hendelsestype.slettet)
        upsertHendelse(repository, failureId)
        repository.settFormueobjektdata(
            syncedId,
            Result.success(formueobjekt(syncedId, syncedHendelseId))
        )
        repository.settFormueobjektdata(
            failureId,
            Result.failure(RuntimeException("gammel feil"))
        )

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", requireHendelseId.toString(), any())
        } returns formueobjekt(requireId, requireHendelseId)

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", requireHendelseId.toString(), any())
        }
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }

        assertThat(repository.hentData(requireId)?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
        assertThat(repository.hentData(syncedId)?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
        assertThat(repository.hentData(deletedId)?.status).isEqualTo(SergDokumentStatus.SLETTET)
        assertThat(repository.hentData(failureId)?.status).isEqualTo(SergDokumentStatus.FEIL)
        assertThat(repository.hentData(failureId)?.kommentar).isEqualTo("gammel feil")
    }

    @Test
    fun `mangler hendelseId gir FEIL uten API-kall`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val id = 2001L
        upsertHendelse(repository, id, hendelseId = null)
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        assertThat(repository.hentData(id)?.status).isEqualTo(SergDokumentStatus.FEIL)
        assertThat(repository.hentData(id)?.kommentar).isEqualTo("Mangler hendelseId")
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
    }

    @Test
    fun `API-suksess lagrer formueobjekt og setter SYNKRONISERT`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val id = 3001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        val response = formueobjekt(id, hendelseId)
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        } returns response

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.hentData(id)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
        assertThat(data?.formueobjekt).isEqualTo(response)
        assertThat(data?.kommentar).isNull()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        }
    }

    @Test
    fun `API-feil setter FEIL med feilmelding`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val id = 4001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        } throws RuntimeException("SERG formueobjekt utilgjengelig")

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.hentData(id)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.FEIL)
        assertThat(data?.kommentar).isEqualTo("SERG formueobjekt utilgjengelig")
    }

    @Test
    fun `fortsetter med neste dokument når ett API-kall feiler`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val failId = 5001L
        val okId = 5002L
        val failHendelseId = upsertHendelse(repository, failId)
        val okHendelseId = upsertHendelse(repository, okId)
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", failHendelseId.toString(), any())
        } throws RuntimeException("første feilet")
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", okHendelseId.toString(), any())
        } returns formueobjekt(okId, okHendelseId)

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        assertThat(repository.hentData(failId)?.status).isEqualTo(SergDokumentStatus.FEIL)
        assertThat(repository.hentData(failId)?.kommentar).isEqualTo("første feilet")
        assertThat(repository.hentData(okId)?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
        verify(exactly = 3) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", failHendelseId.toString(), any())
        }
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", okHendelseId.toString(), any())
        }
    }

    @Test
    fun `sender riktige API-parametre`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val id = 6001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        } returns formueobjekt(id, hendelseId)

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        }
    }

    @Test
    fun `behandler maks 10 dokumenter per kjøring`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val idsByHendelseId = (1L..12L).associate { id ->
            val hendelseId = upsertHendelse(repository, id)
            hendelseId.toString() to id
        }
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", any(), any())
        } answers {
            val hid = secondArg<String>()
            val id = idsByHendelseId.getValue(hid)
            formueobjekt(id, UUID.fromString(hid))
        }

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 10) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", any(), any())
        }
        assertThat(repository.listEtterStatus(SergDokumentStatus.SYNKRONISERT, 100).size).isEqualTo(10)
        assertThat(repository.listEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING, 100).size).isEqualTo(2)
    }

    @Test
    fun `tidligere kommentar nullstilles ved vellykket synk`() = runBlocking {
        val repository = SergDokumentRepository(dataSource())
        val id = 7001L
        upsertHendelse(repository, id, sekvensnummer = 7001L)
        repository.settFormueobjektdata(
            id,
            Result.failure(RuntimeException("gammel feil"))
        )
        val nyHendelseId = upsertHendelse(repository, id, sekvensnummer = 7002L)
        assertThat(repository.hentData(id)?.status).isEqualTo(SergDokumentStatus.KREVER_SYNKRONISERING)
        assertThat(repository.hentData(id)?.kommentar).isEqualTo("gammel feil")

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", nyHendelseId.toString(), any())
        } returns formueobjekt(id, nyHendelseId)

        val result = FormuesobjektSyncService(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.hentData(id)
        assertThat(data?.status).isEqualTo(SergDokumentStatus.SYNKRONISERT)
        assertThat(data?.kommentar).isNull()
    }

    @Test
    fun `uventet DB-feil gir feil-resultat`() = runBlocking {
        val failingDataSource = mockk<DataSource>()
        every { failingDataSource.connection } throws SQLException("db down")
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = FormuesobjektSyncService(failingDataSource, api).sync()

        assertThat(result).isFailure().isInstanceOf(SQLException::class).hasMessage("db down")
    }

    private suspend fun upsertHendelse(
        repository: SergDokumentRepository,
        matrikkelenhetId: Long,
        hendelseId: UUID? = UUID.randomUUID(),
        type: Hendelsestype = Hendelsestype.ny,
        sekvensnummer: Long = nesteSekvensnummer(),
    ): UUID {
        repository.upsertFraHendelse(
            Hendelse(
                sekvensnummer = sekvensnummer,
                hendelseidentifikator = hendelseId,
                matrikkelUnikIdentifikator = matrikkelenhetId,
                hendelsestype = type,
                kommunenummer = "0301",
            )
        )
        return hendelseId ?: UUID.randomUUID()
    }

    private fun nesteSekvensnummer(): Long = ++nesteSekvensnummer

    private fun formueobjekt(id: Long, hendelseId: UUID): FastEiendomSomFormuesobjekt {
        return FastEiendomSomFormuesobjekt(
            identifikator = FormuesobjektIdentifikator(
                matrikkelUnikIdentifikator = id,
            ),
            hendelsesidentifikator = hendelseId,
        )
    }
}
