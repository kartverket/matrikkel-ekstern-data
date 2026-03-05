package no.kartverket.matrikkel.serg

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.serg.formueobjekt.FormuesobjektSyncService
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncService
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDokument
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eieropplysninger
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FormuesobjektIdentifikator
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Personidentifikator
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelser
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import org.openapitools.client.infrastructure.ClientException
import java.util.UUID
import kotlin.random.Random

class SyncFullTest : WithDatabase {
    @Test
    fun `should read all data`() = runBlocking {
        val (ctrl, hendelseApi, formueobjektApi) = SergMock().build()

        ctrl
            .lagFormueobjekt(1000)
            .randomEndringer(500)
            .randomEndringer(500)
            .randomEndringer(500)
            .slettFormueobjekt(100)

        val kvRepo = KeyValueRepository(dataSource())
        val sergDokumentRepo = SergDokumentRepository(dataSource())
        val hendelserSync = HendelserSyncService(dataSource(), hendelseApi)
        val formueobjektSync = FormuesobjektSyncService(dataSource(), formueobjektApi)

        assertThat(kvRepo.getValue("sekvensnummer")).isEqualTo("1")

        synchronizeHendelser(hendelserSync)

        assertThat(kvRepo.getValue("sekvensnummer")).isEqualTo("2601")
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING, limit = 3000)).hasSize(900)
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.SLETTET, limit = 1000)).hasSize(100)

        val matrikkelenhetId = ctrl.randomMatrikkelenhetId()
        assertThat(sergDokumentRepo.hentData(matrikkelenhetId)).isNotNull().all {
            prop(SergDokument::hendelse).isNotNull()
            prop(SergDokument::formueobjekt).isNull()
        }

        synchronizeFormueobjekt(formueobjektSync)

        assertThat(sergDokumentRepo.hentData(matrikkelenhetId)).isNotNull().all {
            prop(SergDokument::hendelse).isNotNull()
            prop(SergDokument::formueobjekt).isNotNull()
        }
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING, limit = 3000)).hasSize(0)
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.SYNKRONISERT, limit = 3000)).hasSize(900)


        ctrl
            .slettFormueobjekt(100)
            .randomEndringer(200)

        synchronizeHendelser(hendelserSync)

        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.KREVER_SYNKRONISERING, limit = 3000)).hasSize(200)
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.SLETTET, limit = 3000)).hasSize(200)

        synchronizeFormueobjekt(formueobjektSync)
        assertThat(sergDokumentRepo.listEtterStatus(SergDokumentStatus.SYNKRONISERT, limit = 3000)).hasSize(800)
    }
}

private suspend fun synchronizeFormueobjekt(formueobjektSync: FormuesobjektSyncService) {
    do {
        val result = formueobjektSync.sync()
        val antall = result.fold(
            onSuccess = { it },
            onFailure = { 0 }
        )
    } while (antall > 0)
}

private suspend fun synchronizeHendelser(hendelserSync: HendelserSyncService) {
    do {
        val result = hendelserSync.sync(500)
        val antall = result.fold(
            onSuccess = { it.size },
            onFailure = { 0 }
        )
    } while (antall > 0)
}

class SergMock {
    private val rng = Random(0)
    private var sekvensnummer: Long = 2
    private val hendelser: MutableList<Hendelse> = mutableListOf()
    private val formueobjekter: MutableMap<Long, FastEiendomSomFormuesobjekt> = mutableMapOf()
    private val hendelseMatrikkelenhetId: MutableMap<UUID, Long> = mutableMapOf()

    fun lagFormueobjekt(antall: Int): SergMock {
        repeat(antall) {
            val matrikkelUnikIdentifikator = rng.nextLong()
            val hendelsesidentifikator = UUID.nameUUIDFromBytes(rng.nextBytes(5))

            val formueobjekt = FastEiendomSomFormuesobjekt(
                identifikator = FormuesobjektIdentifikator(
                    skatteetatensEiendomsidentifikator = rng.nextLong(),
                    matrikkelUnikIdentifikator = matrikkelUnikIdentifikator,
                ),
                hendelsesidentifikator = hendelsesidentifikator,
                eieropplysninger = listOf(
                    Eieropplysninger(
                        personidentifikator = Personidentifikator(
                            organisasjonsnummer = rng.nextBytes(10).toHexString()
                        ),
                    )
                )
            )

            formueobjekter[matrikkelUnikIdentifikator] = formueobjekt
            hendelser.add(formueobjekt.createHendelse(Hendelsestype.ny))
            hendelseMatrikkelenhetId[hendelsesidentifikator] = matrikkelUnikIdentifikator
        }
        return this
    }

    fun randomEndringer(antallEndringer: Int): SergMock {
        val keys = formueobjekter.keys.randomSubset(antallEndringer)
        for (endringsKey in keys) {
            val formueobjekt = requireNotNull(formueobjekter[endringsKey])
            val matrikkelUnikIdentifikator = requireNotNull(formueobjekt.identifikator?.matrikkelUnikIdentifikator)
            val hendelsesidentifikator = UUID.nameUUIDFromBytes(rng.nextBytes(5))

            val nyttformueobjekt = formueobjekt.copy(
                hendelsesidentifikator = hendelsesidentifikator,
                eieropplysninger = listOf(
                    Eieropplysninger(
                        personidentifikator = Personidentifikator(
                            foedselsnummer = rng.nextBytes(10).toHexString()
                        )
                    )
                )
            )

            formueobjekter[matrikkelUnikIdentifikator] = nyttformueobjekt
            hendelser.add(formueobjekt.createHendelse(Hendelsestype.endret))
            hendelseMatrikkelenhetId[hendelsesidentifikator] = matrikkelUnikIdentifikator
        }
        return this
    }

    fun slettFormueobjekt(antall: Int): SergMock {
        val keys = formueobjekter.keys.randomSubset(antall)
        for (endringsKey in keys) {
            val formueobjekt = requireNotNull(formueobjekter[endringsKey])
            val matrikkelUnikIdentifikator = requireNotNull(formueobjekt.identifikator?.matrikkelUnikIdentifikator)
            val hendelsesidentifikator = UUID.nameUUIDFromBytes(rng.nextBytes(5))

            formueobjekter.remove(matrikkelUnikIdentifikator)
            hendelser.add(formueobjekt.copy(hendelsesidentifikator = hendelsesidentifikator).createHendelse(Hendelsestype.slettet))
            hendelseMatrikkelenhetId[hendelsesidentifikator] = matrikkelUnikIdentifikator
        }
        return this
    }

    fun build(): Triple<SergMock, HendelserApi, FormuesobjektFastEiendomApi> {
        val hendelserApi = mockk<HendelserApi>()
        val formueobjektApi = mockk<FormuesobjektFastEiendomApi>()

        every { hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any()) } answers {
            val fraSekvens = firstArg<Long>()
            val antall = secondArg<Int>()

            Hendelser(
                hendelser = hendelser
                    .filter { fraSekvens < (it.sekvensnummer ?: -1) }
                    .take(antall)
            )
        }

        every { formueobjektApi.hentFormuesobjektFastEiendom(any(), any(), any()) } answers {
            val hendelseidentifikator = UUID.fromString(secondArg<String>())
            val matrikkelenhetId = hendelseMatrikkelenhetId[hendelseidentifikator] ?: throw ClientException("fant ikke matrikkelenhetId")
            formueobjekter[matrikkelenhetId] ?: throw ClientException("fant ikke formueobjekt")
        }

        return Triple(this, hendelserApi, formueobjektApi)
    }

    fun FastEiendomSomFormuesobjekt.createHendelse(type: Hendelsestype): Hendelse {
        return Hendelse(
            sekvensnummer = sekvensnummer++,
            hendelseidentifikator = this.hendelsesidentifikator,
            skatteetatensEiendomsidentifikator = this.identifikator?.skatteetatensEiendomsidentifikator,
            matrikkelUnikIdentifikator = this.identifikator?.matrikkelUnikIdentifikator,
            hendelsestype = type,
            kommunenummer = "0001"
        )

    }

    fun randomMatrikkelenhetId(): Long {
        return formueobjekter.keys.random(rng)
    }
}

private fun <T> Collection<T>.randomSubset(k: Int, rng: Random = Random.Default): List<T> {
    require(k >= 0) { "k must be >= 0" }
    require(k <= size) { "k must be <= size" }
    return shuffled(rng).take(k)
}