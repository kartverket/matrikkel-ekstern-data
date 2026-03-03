package no.kartverket.matrikkel.serg

import assertk.assertThat
import assertk.assertions.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.serg.repository.SergDocumentRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentStatus
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

class SyncFormueobjektTest : WithDatabase {
    @Test
    fun `ingen dokumenter å synkronisere`() = runBlocking {
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
    }

    @Test
    fun `henter kun REQUIRE_SYNCHRONIZATION`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
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

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", requireHendelseId.toString(), any())
        }
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }

        assertThat(repository.getData(requireId)?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
        assertThat(repository.getData(syncedId)?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
        assertThat(repository.getData(deletedId)?.status).isEqualTo(SergDocumentStatus.DELETED)
        assertThat(repository.getData(failureId)?.status).isEqualTo(SergDocumentStatus.FAILURE)
        assertThat(repository.getData(failureId)?.kommentar).isEqualTo("gammel feil")
    }

    @Test
    fun `mangler hendelseId gir FAILURE uten API-kall`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
        val id = 2001L
        upsertHendelse(repository, id, hendelseId = null)
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        assertThat(repository.getData(id)?.status).isEqualTo(SergDocumentStatus.FAILURE)
        assertThat(repository.getData(id)?.kommentar).isEqualTo("Mangler hendelseId")
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
    }

    @Test
    fun `API-suksess lagrer formueobjekt og setter SYNCHRONIZED`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
        val id = 3001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        val response = formueobjekt(id, hendelseId)
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        } returns response

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.getData(id)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
        assertThat(data?.formueobjekt).isEqualTo(response)
        assertThat(data?.kommentar).isNull()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        }
    }

    @Test
    fun `API-feil setter FAILURE med feilmelding`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
        val id = 4001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        } throws RuntimeException("SERG formueobjekt utilgjengelig")

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.getData(id)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.FAILURE)
        assertThat(data?.kommentar).isEqualTo("SERG formueobjekt utilgjengelig")
    }

    @Test
    fun `fortsetter med neste dokument når ett API-kall feiler`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
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

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        assertThat(repository.getData(failId)?.status).isEqualTo(SergDocumentStatus.FAILURE)
        assertThat(repository.getData(failId)?.kommentar).isEqualTo("første feilet")
        assertThat(repository.getData(okId)?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
        verify(exactly = 3) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", failHendelseId.toString(), any())
        }
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", okHendelseId.toString(), any())
        }
    }

    @Test
    fun `sender riktige API-parametre`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
        val id = 6001L
        val hendelseId = upsertHendelse(repository, id)
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        } returns formueobjekt(id, hendelseId)

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelseId.toString(), any())
        }
    }

    @Test
    fun `behandler maks 10 dokumenter per kjøring`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
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

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        verify(exactly = 10) {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", any(), any())
        }
        assertThat(repository.listByStatus(SergDocumentStatus.SYNCHRONIZED, 100).size).isEqualTo(10)
        assertThat(repository.listByStatus(SergDocumentStatus.REQUIRE_SYNCHRONIZATION, 100).size).isEqualTo(2)
    }

    @Test
    fun `tidligere kommentar nullstilles ved vellykket synk`() = runBlocking {
        val repository = SergDocumentRepository(dataSource())
        val id = 7001L
        upsertHendelse(repository, id)
        repository.settFormueobjektdata(
            id,
            Result.failure(RuntimeException("gammel feil"))
        )
        val nyHendelseId = upsertHendelse(repository, id)
        assertThat(repository.getData(id)?.status).isEqualTo(SergDocumentStatus.REQUIRE_SYNCHRONIZATION)
        assertThat(repository.getData(id)?.kommentar).isEqualTo("gammel feil")

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", nyHendelseId.toString(), any())
        } returns formueobjekt(id, nyHendelseId)

        val result = SyncFormueobject(dataSource(), api).sync()

        assertThat(result).isSuccess()
        val data = repository.getData(id)
        assertThat(data?.status).isEqualTo(SergDocumentStatus.SYNCHRONIZED)
        assertThat(data?.kommentar).isNull()
    }

    @Test
    fun `uventet DB-feil gir failure-resultat`() = runBlocking {
        val failingDataSource = mockk<DataSource>()
        every { failingDataSource.connection } throws SQLException("db down")
        val api = mockk<FormuesobjektFastEiendomApi>()

        val result = SyncFormueobject(failingDataSource, api).sync()

        assertThat(result).isFailure().isInstanceOf(SQLException::class).hasMessage("db down")
    }

    private suspend fun upsertHendelse(
        repository: SergDocumentRepository,
        id: Long,
        hendelseId: UUID? = UUID.randomUUID(),
        type: Hendelsestype = Hendelsestype.ny,
    ): UUID {
        repository.upsertFraHendelse(
            Hendelse(
                sekvensnummer = id,
                hendelseidentifikator = hendelseId,
                matrikkelUnikIdentifikator = id,
                hendelsestype = type,
                kommunenummer = "0301",
            )
        )
        return hendelseId ?: UUID.randomUUID()
    }

    private fun formueobjekt(id: Long, hendelseId: UUID): FastEiendomSomFormuesobjekt {
        return FastEiendomSomFormuesobjekt(
            identifikator = FormuesobjektIdentifikator(
                matrikkelUnikIdentifikator = id,
            ),
            hendelsesidentifikator = hendelseId,
        )
    }
}
