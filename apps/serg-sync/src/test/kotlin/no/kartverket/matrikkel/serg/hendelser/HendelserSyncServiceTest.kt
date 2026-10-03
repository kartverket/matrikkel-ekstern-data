package no.kartverket.matrikkel.serg.hendelser

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSuccess
import assertk.assertions.messageContains
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelser
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class HendelserSyncServiceTest : WithDatabase {

    private val kafkaSergHendelserFeedProducer = mockk<MessageProducer<Long, Hendelse>>()

    @BeforeEach
    fun setup() {
        coEvery { kafkaSergHendelserFeedProducer.send(any()) } returns CompletableDeferred(Unit)
    }

    @Test
    fun `start fra 1 om sekvensnumemr ikke er satt`() = runTest {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.delete("sekvensnummer")

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }
        coVerify(exactly = 0) { kafkaSergHendelserFeedProducer.send(any()) }
    }

    @Test
    fun `plukker opp sekvensnummer om satt`() = runTest {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "123")

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(123L, 1000, any())
        }
        coVerify(exactly = 0) { kafkaSergHendelserFeedProducer.send(any()) }
    }

    @Test
    fun `feil ved henting av sekvensnummer rapporteres`() = runTest {
        val hendelserApi = gittHendelseApiSomReturnerer(emptyList())
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "ikke_tall")

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result)
            .isFailure()
            .isInstanceOf(NumberFormatException::class)

        verify(exactly = 0) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any())
        }
        coVerify(exactly = 0) { kafkaSergHendelserFeedProducer.send(any()) }
    }

    @Test
    fun `henter hendelser fra SERG`() = runTest {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "1")
        val dokumentRepository = SergDokumentRepository(dataSource())
        val hendelser = listOf(
            hendelse(id = 1001L, type = Hendelsestype.ny, seq = 10L),
            hendelse(id = 1002L, type = Hendelsestype.slettet, seq = 11L),
        )
        val hendelserApi = gittHendelseApiSomReturnerer(hendelser)

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result)
            .isSuccess()
            .isEqualTo(hendelser)

        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }

        hendelser.forEach { hendelse ->
            coVerify(exactly = 1) {
                kafkaSergHendelserFeedProducer.send(match { it.value == hendelse })
            }
        }

        val nyHendelseData = dokumentRepository.hentData(1001L)
        assertThat(nyHendelseData?.hendelse).isEqualTo(hendelser[0])
        assertThat(nyHendelseData?.status).isEqualTo(SergDokumentStatus.KREVER_SYNKRONISERING)

        val slettetHendelseData = dokumentRepository.hentData(1002L)
        assertThat(slettetHendelseData?.hendelse).isEqualTo(hendelser[1])
        assertThat(slettetHendelseData?.status).isEqualTo(SergDokumentStatus.SLETTET)

        assertThat(keyValueRepository.getValue("sekvensnummer")).isEqualTo("11")
    }

    @Test
    fun `feil ved henting av hendelser rapporteres`() = runTest {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "123")
        val hendelserApi = mockk<HendelserApi>()
        every {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(any(), any(), any())
        } throws RuntimeException("SERG unavailable")

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result)
            .isFailure()
            .isInstanceOf(RuntimeException::class)
            .hasMessage("SERG unavailable")

        verify(exactly = 3) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(123L, 1000, any())
        }
        coVerify(exactly = 0) { kafkaSergHendelserFeedProducer.send(any()) }
        assertThat(keyValueRepository.getValue("sekvensnummer")).isEqualTo("123")
    }

    @Test
    fun `lagrer alle hendelser og rapporterer om eventuelle ugyldige data`() = runTest {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "1")
        val dokumentRepository = SergDokumentRepository(dataSource())
        val hendelser = listOf(
            hendelse(id = 2001L, type = Hendelsestype.ny, seq = 12L),
            hendelse(id = null, type = Hendelsestype.endret, seq = 13L), // Invalid, missing ID
            hendelse(id = 2002L, type = Hendelsestype.slettet, seq = 14L)
        )
        val hendelserApi = gittHendelseApiSomReturnerer(hendelser)

        val result = HendelserSyncService(dataSource(), hendelserApi, kafkaSergHendelserFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            hendelserApi.hentHendelserFormuesobjektFastEiendom(1L, 1000, any())
        }
        assertThat(dokumentRepository.hentData(2001L)).isNotNull()
        assertThat(dokumentRepository.hentData(2002L)).isNotNull()

        hendelser.forEach { hendelse ->
            val antall: Int = if (hendelse.matrikkelUnikIdentifikator != null) 1 else 0
            coVerify(exactly = antall) {
                kafkaSergHendelserFeedProducer.send(match { it.value == hendelse })
            }
        }

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

    @Test
    fun `beholder sekvensnummer når Kafka-publisering feiler`() = runTest {
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue("sekvensnummer", "123")

        val hendelse = hendelse(
            id = 1001L,
            type = Hendelsestype.ny,
            seq = 124L,
        )
        val hendelserApi = gittHendelseApiSomReturnerer(listOf(hendelse))

        val feiletSending = CompletableDeferred<Unit>().apply {
            completeExceptionally(RuntimeException("Kafka utilgjengelig"))
        }
        coEvery {
            kafkaSergHendelserFeedProducer.send(any())
        } returns feiletSending

        val resultat = HendelserSyncService(
            dataSource(),
            hendelserApi,
            kafkaSergHendelserFeedProducer,
        ).sync()

        assertThat(resultat)
            .isFailure()
            .messageContains("Kafka utilgjengelig")

        assertThat(keyValueRepository.getValue("sekvensnummer"))
            .isEqualTo("123")

        coVerify(exactly = 1) {
            kafkaSergHendelserFeedProducer.send(
                match { it.key == 1001L && it.value == hendelse },
            )
        }
    }

    @Test
    fun `beholder sekvensnummer når én hendelse publiseres og én hendelse feiler`() =
        runTest {
            val opprinneligSekvensnummer = 123L
            val keyValueRepository = KeyValueRepository(dataSource())
            keyValueRepository.setValue(
                "sekvensnummer",
                opprinneligSekvensnummer.toString(),
            )

            val forsteHendelse = hendelse(
                id = 1001L,
                type = Hendelsestype.ny,
                seq = 124L,
            )
            val andreHendelse = hendelse(
                id = 1002L,
                type = Hendelsestype.endret,
                seq = 125L,
            )
            val hendelserApi = gittHendelseApiSomReturnerer(
                listOf(forsteHendelse, andreHendelse),
            )

            val feiletSending = CompletableDeferred<Unit>().apply {
                completeExceptionally(
                    RuntimeException("Kafka-publisering feilet"),
                )
            }

            coEvery {
                kafkaSergHendelserFeedProducer.send(any())
            } answers {
                when (firstArg<ProducerRecord<Long, Hendelse>>().key) {
                    1001L -> CompletableDeferred(Unit)
                    1002L -> feiletSending
                    else -> error("Uventet Kafka-nøkkel")
                }
            }

            val resultat = HendelserSyncService(
                dataSource = dataSource(),
                hendelserApi = hendelserApi,
                messageProducer = kafkaSergHendelserFeedProducer,
            ).sync()

            assertThat(resultat)
                .isFailure()
                .hasMessage("Kafka-publisering feilet")

            assertThat(keyValueRepository.getValue("sekvensnummer"))
                .isEqualTo(opprinneligSekvensnummer.toString())

            coVerify(exactly = 1) {
                kafkaSergHendelserFeedProducer.send(
                    match {
                        it.key == 1001L &&
                                it.value == forsteHendelse
                    },
                )
            }
            coVerify(exactly = 1) {
                kafkaSergHendelserFeedProducer.send(
                    match {
                        it.key == 1002L &&
                                it.value == andreHendelse
                    },
                )
            }
        }

    @Test
    fun `beholder sekvensnummer når Kafka-publisering får timeout`() = runTest {
        val opprinneligSekvensnummer = 123L
        val keyValueRepository = KeyValueRepository(dataSource())
        keyValueRepository.setValue(
            "sekvensnummer",
            opprinneligSekvensnummer.toString(),
        )

        val hendelse = hendelse(
            id = 1001L,
            type = Hendelsestype.ny,
            seq = 124L,
        )
        val hendelserApi =
            gittHendelseApiSomReturnerer(listOf(hendelse))

        // Denne fullføres aldri og utløser tjenestens timeout.
        val sendingSomAldriFullfores = CompletableDeferred<Unit>()

        coEvery {
            kafkaSergHendelserFeedProducer.send(any())
        } returns sendingSomAldriFullfores

        val resultat = HendelserSyncService(
            dataSource = dataSource(),
            hendelserApi = hendelserApi,
            messageProducer = kafkaSergHendelserFeedProducer,
        ).sync()

        assertThat(resultat)
            .isFailure()
            .isInstanceOf(TimeoutCancellationException::class)

        assertThat(keyValueRepository.getValue("sekvensnummer"))
            .isEqualTo(opprinneligSekvensnummer.toString())

        coVerify(exactly = 1) {
            kafkaSergHendelserFeedProducer.send(
                match {
                    it.key == 1001L &&
                            it.value == hendelse
                },
            )
        }
    }
}