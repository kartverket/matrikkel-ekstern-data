package no.kartverket.matrikkel.serg.formueobjekt

import no.kartverket.eksterndata.domene.Serg.FastEiendomSomFormuesObjektHendelse
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isSuccess
import assertk.assertions.messageContains
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.kafkaclient.ConsumerRecord
import no.kartverket.matrikkel.kafkaclient.ConsumerRecords
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
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
import kotlin.random.Random
import kotlin.time.Clock

class FormueobjektSyncServiceTest : WithDatabase {
    private var nesteSekvensnummer = 0L

    @Test
    fun `ingen dokumenter å synkronisere`() = runBlocking {
        val api = mockk<FormuesobjektFastEiendomApi>()
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) } returns ConsumerRecords("", emptyList())
        coEvery { kafkaSergHendelserFeedConsumer.commitSync() } returns Unit

        val result = FormuesobjektSyncService(api, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
        coVerify(exactly = 0) { kafkaSergFormuesobjektFeedProducer.send(any()) }
    }

    @Test
    fun `kan synkronisere hendelser`() = runBlocking {
        // Hendelser mock
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()
        val hendelser = listOf(
            lagRecord(1001L, UUID.randomUUID()),
            lagRecord(1002L, UUID.randomUUID())
        )
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) }returns ConsumerRecords("", hendelser)
        coEvery { kafkaSergHendelserFeedConsumer.commitSync() } returns Unit

        // API mock
        val api = mockk<FormuesobjektFastEiendomApi>()
        hendelser.forEach { hendelse ->
            every {
                api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelse.value?.hendelseidentifikator.toString(), any())
            } returns formueobjekt(hendelse.key, hendelse.value?.hendelseidentifikator!!)
        }

        // FastEiendomSomFormuesObjektFeedProducer mock
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val sentFormuesobjekt = mutableListOf<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()
        coEvery { kafkaSergFormuesobjektFeedProducer.send(capture(sentFormuesobjekt)) } returns CompletableDeferred(Unit)

        val result = FormuesobjektSyncService(api, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 2) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
        assertThat(sentFormuesobjekt.size).isEqualTo(2)
    }

    @Test
    fun `mangler hendelseId gir FEIL uten API-kall`() = runBlocking {
        val api = mockk<FormuesobjektFastEiendomApi>()
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()

        val hendelser = listOf(lagRecord(2001L, null))
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) } returns ConsumerRecords("", hendelser)
        coEvery { kafkaSergHendelserFeedConsumer.commitSync() } returns Unit

        val result = FormuesobjektSyncService(api, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 0) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
        coVerify(exactly = 0) { kafkaSergFormuesobjektFeedProducer.send(any()) }
    }

    @Test
    fun `API-feil setter FEIL med feilmelding`() = runBlocking {
        // Hendelser mock
        val hendelser = listOf(lagRecord(4001L, UUID.randomUUID()))
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) } returns ConsumerRecords("", hendelser)
        coEvery { kafkaSergHendelserFeedConsumer.commitSync() } returns Unit

        // Producer mock
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        coEvery { kafkaSergFormuesobjektFeedProducer.send(any()) } returns CompletableDeferred(Unit)

        // API mock
        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom("kartverketMatrikkel", any(), any())
        } throws RuntimeException("SERG formueobjekt utilgjengelig")

        val result = FormuesobjektSyncService(api, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer).sync()

        assertThat(result).isFailure()
            .isInstanceOf(RuntimeException::class)
            .messageContains("SERG formueobjekt utilgjengelig")
        coVerify(exactly = 0) { kafkaSergFormuesobjektFeedProducer.send(any()) }
        coVerify(exactly = 0) { kafkaSergHendelserFeedConsumer.commitSync() }
    }

    @Test
    fun `fortsetter med neste dokument når hendelse mangler hendelseidentifikator`() = runBlocking {
        // Hendelser mock
        val kafkaSergHendelserFeedConsumer = mockk<MessageConsumer<Long, Hendelse>>()
        val hendelser = listOf(
            lagRecord(1001L, null),
            lagRecord(1002L, UUID.randomUUID())
        )
        coEvery { kafkaSergHendelserFeedConsumer.poll(any()) }returns ConsumerRecords("", hendelser)
        coEvery { kafkaSergHendelserFeedConsumer.commitSync() } returns Unit

        // API mock
        val api = mockk<FormuesobjektFastEiendomApi>()
        hendelser.filter { it.value?.hendelseidentifikator != null }.forEach { hendelse ->
            every {
                api.hentFormuesobjektFastEiendom("kartverketMatrikkel", hendelse.value?.hendelseidentifikator.toString(), any())
            } returns formueobjekt(hendelse.key, hendelse.value?.hendelseidentifikator!!)
        }

        // FastEiendomSomFormuesObjektFeedProducer mock
        val kafkaSergFormuesobjektFeedProducer = mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val sentFormuesobjekt = mutableListOf<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()
        coEvery { kafkaSergFormuesobjektFeedProducer.send(capture(sentFormuesobjekt)) } returns CompletableDeferred(Unit)

        val result = FormuesobjektSyncService(api, kafkaSergHendelserFeedConsumer, kafkaSergFormuesobjektFeedProducer).sync()

        assertThat(result).isSuccess()
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom(any(), any(), any())
        }
        assertThat(sentFormuesobjekt.size).isEqualTo(1)
        assertThat(sentFormuesobjekt.first().key).isEqualTo(1002L)
    }

    private fun lagRecord(
        matrikkelenhetId: Long,
        hendelseId: UUID? = UUID.randomUUID(),
        type: Hendelsestype = Hendelsestype.ny,
        sekvensnummer: Long = nesteSekvensnummer(),
    ): ConsumerRecord<Long, Hendelse> {
        return ConsumerRecord(
            "",
            nesteSekvensnummer(),
            matrikkelenhetId,
            lagHendelse(matrikkelenhetId, hendelseId, type, sekvensnummer),
            Clock.System.now()
        )
    }

    private fun lagHendelse(
        matrikkelenhetId: Long,
        hendelseId: UUID? = UUID.randomUUID(),
        type: Hendelsestype = Hendelsestype.ny,
        sekvensnummer: Long = nesteSekvensnummer(),
    ): Hendelse {
        var hendelse = Hendelse(
            sekvensnummer = sekvensnummer,
            hendelseidentifikator = hendelseId,
            matrikkelUnikIdentifikator = matrikkelenhetId,
            hendelsestype = type,
            kommunenummer = "0301",
        )
        return hendelse
    }

    private fun nesteSekvensnummer(): Long = ++nesteSekvensnummer

    private fun formueobjekt(id: Long, hendelseId: UUID): FastEiendomSomFormuesobjekt {
        return FastEiendomSomFormuesobjekt(
            identifikator = FormuesobjektIdentifikator(
                matrikkelUnikIdentifikator = id,
            ),
            hendelsesidentifikator = hendelseId,
            eieropplysninger = listOf(
                Eieropplysninger(
                    eierforhold = Eierforhold(eiernivaa = Eiernivaa.eiendomsrett),
                    personidentifikator = Personidentifikator(
                        foedselsnummer = Random(0).nextBytes(10).toHexString()
                    ),
                )
            )
        )
    }
}
