package no.kartverket.matrikkel.serg

import no.kartverket.eksterndata.domene.Serg.FastEiendomSomFormuesObjektHendelse
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.kafkaclient.*
import no.kartverket.matrikkel.serg.formueobjekt.FormuesobjektSyncService
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncService
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eierforhold
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Eiernivaa
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
import kotlin.time.Clock

class SyncFullTest : WithDatabase {

    @Test
    fun `should read all data`() = runBlocking {
        val (ctrl, hendelseApi, formueobjektApi) = SergMock().build()

        val kafkaSergHendelserFeedProducer = mockk<MessageProducer<Long, Hendelse>>()
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()

        ctrl
            .lagFormueobjekt(500)
            .randomEndringer(400)
            .slettFormueobjekt(100)

        val sentHendelser = mutableListOf<ProducerRecord<Long, Hendelse>>()
        coEvery { kafkaSergHendelserFeedProducer.send(capture(sentHendelser)) } returns CompletableDeferred(Unit)

        val kvRepo = KeyValueRepository(dataSource())
        val hendelserSync = HendelserSyncService(dataSource(), hendelseApi, kafkaSergHendelserFeedProducer)
        val formueobjektSync = FormuesobjektSyncService(formueobjektApi, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer)

        assertThat(kvRepo.getValue("sekvensnummer")).isEqualTo("1")

        synchronizeHendelser(hendelserSync)

        assertThat(kvRepo.getValue("sekvensnummer")).isEqualTo("1001")
        assertThat(ctrl.hendelser.size).isEqualTo(sentHendelser.size)
        assertThat(sentHendelser.stream().filter { it.value!!.hendelsestype == Hendelsestype.ny }.toList()).hasSize(500)
        assertThat(sentHendelser.stream().filter { it.value!!.hendelsestype == Hendelsestype.endret }.toList()).hasSize(400)
        assertThat(sentHendelser.stream().filter { it.value!!.hendelsestype == Hendelsestype.slettet }.toList()).hasSize(100)

        val sentFormuesobjekt = mutableListOf<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()
        coEvery { kafkaSergFormuesobjektFeedProducer.send(capture(sentFormuesobjekt)) } returns CompletableDeferred(Unit)
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) } returns ConsumerRecords("",
            records = sentHendelser.map { ConsumerRecord("", it.value!!.sekvensnummer!!, it.key, it.value, Clock.System.now()) }
        )

        synchronizeFormueobjekt(formueobjektSync)

        assertThat(sentFormuesobjekt.size).isEqualTo(1000)
    }
}

private suspend fun synchronizeFormueobjekt(formueobjektSync: FormuesobjektSyncService) {
    do {
        val result = formueobjektSync.sync(antall = 1000)
        val antall = result.fold(
            onSuccess = { it },
            onFailure = { 0 }
        )
    } while (antall > 0)
}

private suspend fun synchronizeHendelser(hendelserSync: HendelserSyncService) {
    do {
        val result = hendelserSync.sync(1000)
        val antall = result.fold(
            onSuccess = { it.size },
            onFailure = { 0 }
        )
    } while (antall > 0)
}

class SergMock {
    private val rng = Random(0)
    private var sekvensnummer: Long = 2
    val hendelser: MutableList<Hendelse> = mutableListOf()
    private val formueobjekter: MutableMap<UUID, FastEiendomSomFormuesobjekt> = mutableMapOf()

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
                        eierforhold = Eierforhold(eiernivaa = Eiernivaa.eiendomsrett),
                        personidentifikator = Personidentifikator(
                            organisasjonsnummer = rng.nextBytes(10).toHexString()
                        ),
                    )
                )
            )

            hendelser.add(formueobjekt.createHendelse(Hendelsestype.ny))
            formueobjekter[hendelsesidentifikator] = formueobjekt
        }
        return this
    }

    fun randomEndringer(antallEndringer: Int): SergMock {
        val keys = formueobjekter.keys.randomSubset(antallEndringer)
        for (endringsKey in keys) {
            val formueobjekt = requireNotNull(formueobjekter[endringsKey])
            val hendelsesidentifikator = UUID.nameUUIDFromBytes(rng.nextBytes(5))

            val nyttformueobjekt = formueobjekt.copy(
                hendelsesidentifikator = hendelsesidentifikator,
                eieropplysninger = listOf(
                    Eieropplysninger(
                        eierforhold = Eierforhold(eiernivaa = Eiernivaa.feste),
                        personidentifikator = Personidentifikator(
                            foedselsnummer = rng.nextBytes(10).toHexString()
                        )
                    )
                )
            )

            hendelser.add(nyttformueobjekt.createHendelse(Hendelsestype.endret))
            formueobjekter[hendelsesidentifikator] = nyttformueobjekt
        }
        return this
    }

    fun slettFormueobjekt(antall: Int): SergMock {
        val keys = formueobjekter.keys.randomSubset(antall)
        for (endringsKey in keys) {
            val formueobjekt = requireNotNull(formueobjekter[endringsKey])
            val hendelsesidentifikator = UUID.nameUUIDFromBytes(rng.nextBytes(5))

            val nyttformueobjekt = formueobjekt.copy(hendelsesidentifikator = hendelsesidentifikator)

            hendelser.add(nyttformueobjekt.createHendelse(Hendelsestype.slettet))
            formueobjekter[hendelsesidentifikator] = nyttformueobjekt
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
            formueobjekter[hendelseidentifikator] ?: throw ClientException("fant ikke formueobjekt: $hendelseidentifikator")
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
}

private fun <T> Collection<T>.randomSubset(k: Int, rng: Random = Random.Default): List<T> {
    require(k >= 0) { "k must be >= 0" }
    require(k <= size) { "k must be <= size" }
    return shuffled(rng).take(k)
}