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
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import no.kartverket.eksterndata.domene.Serg
import no.kartverket.matrikkel.kafkaclient.ConsumerRecord
import no.kartverket.matrikkel.kafkaclient.ConsumerRecords
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import no.kartverket.matrikkel.kafkaclient.ProducerRecord
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
import org.openapitools.client.infrastructure.ClientError
import org.openapitools.client.infrastructure.ClientException
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Clock

class FormueobjektSyncServiceTest {
    private var nesteSekvensnummer = 0L

    @Test
    fun `ingen dokumenter å synkronisere`() = runTest {
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
    fun `kan synkronisere hendelser`() = runTest {
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
    fun `mangler hendelseId gir FEIL uten API-kall`() = runTest {
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
    fun `API-feil setter FEIL med feilmelding`() = runTest {
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
    fun `fortsetter med neste dokument når hendelse mangler hendelseidentifikator`() = runTest {
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
        val hendelse = Hendelse(
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

    @Test
    fun `committer ikke consumer-offset når publisering av eneste melding feiler`() = runTest {
        val hendelseId = UUID.randomUUID()
        val record = lagRecord(1001L, hendelseId)

        val consumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery { consumer.poll(any()) } returns ConsumerRecords("", listOf(record))

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(
                "kartverketMatrikkel",
                hendelseId.toString(),
                any(),
            )
        } returns formueobjekt(1001L, hendelseId)

        val producer =
            mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val feiletSending = CompletableDeferred<Unit>().apply {
            completeExceptionally(RuntimeException("Kafka utilgjengelig"))
        }
        coEvery { producer.send(any()) } returns feiletSending

        val resultat = FormuesobjektSyncService(api, consumer, producer).sync()

        assertThat(resultat)
            .isFailure()
            .messageContains("Kafka utilgjengelig")

        coVerify(exactly = 1) { producer.send(any()) }
        coVerify(exactly = 0) { consumer.commitSync() }
    }

    @Test
    fun `committer ikke consumer-offset når én melding publiseres og én melding feiler`() =
        runTest {
            val forsteHendelseId = UUID.randomUUID()
            val andreHendelseId = UUID.randomUUID()

            val records = listOf(
                lagRecord(1001L, forsteHendelseId),
                lagRecord(1002L, andreHendelseId),
            )

            val consumer = mockk<MessageConsumer<Long, Hendelse>>()
            coEvery {
                consumer.poll(any())
            } returns ConsumerRecords("", records)

            val api = mockk<FormuesobjektFastEiendomApi>()
            every {
                api.hentFormuesobjektFastEiendom(
                    "kartverketMatrikkel",
                    forsteHendelseId.toString(),
                    any(),
                )
            } returns formueobjekt(1001L, forsteHendelseId)

            every {
                api.hentFormuesobjektFastEiendom(
                    "kartverketMatrikkel",
                    andreHendelseId.toString(),
                    any(),
                )
            } returns formueobjekt(1002L, andreHendelseId)

            val producer =
                mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()

            val sendteRecords =
                mutableListOf<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()

            val vellykketSending = CompletableDeferred(Unit)
            val feiletSending = CompletableDeferred<Unit>().apply {
                completeExceptionally(
                    RuntimeException("Kafka-publisering feilet"),
                )
            }

            coEvery {
                producer.send(capture(sendteRecords))
            } answers {
                val record =
                    firstArg<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()

                when (record.key) {
                    1001L -> vellykketSending
                    1002L -> feiletSending
                    else -> error("Uventet Kafka-nøkkel: ${record.key}")
                }
            }

            val resultat = FormuesobjektSyncService(
                api,
                consumer,
                producer,
            ).sync()

            assertThat(resultat)
                .isFailure()
                .messageContains("Kafka-publisering feilet")

            assertThat(sendteRecords.map { it.key }.toSet())
                .isEqualTo(setOf(1001L, 1002L))

            coVerify(exactly = 2) {
                producer.send(any())
            }
            coVerify(exactly = 0) {
                consumer.commitSync()
            }
        }

    @Test
    fun `publiserer forventet Kafka-nøkkel og komplett formuesobjekt`() = runTest {
        val hendelseId =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val matrikkelenhetId = 1001L

        val consumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery {
            consumer.poll(any())
        } returns ConsumerRecords(
            "",
            listOf(
                lagRecord(
                    matrikkelenhetId = matrikkelenhetId,
                    hendelseId = hendelseId,
                    type = Hendelsestype.ny,
                ),
            ),
        )
        coEvery {
            consumer.commitSync()
        } returns Unit

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(
                "kartverketMatrikkel",
                hendelseId.toString(),
                any(),
            )
        } returns FastEiendomSomFormuesobjekt(
            identifikator = FormuesobjektIdentifikator(
                matrikkelUnikIdentifikator = matrikkelenhetId,
            ),
            hendelsesidentifikator = hendelseId,
            rettighetshaverMangler = false,
            eieropplysninger = listOf(
                Eieropplysninger(
                    personidentifikator = Personidentifikator(
                        foedselsnummer = "01019012345",
                    ),
                    eierforhold = Eierforhold(
                        eiernivaa = Eiernivaa.eiendomsrett,
                    ),
                ),
            ),
        )

        val producer =
            mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()
        val publisertRecord =
            slot<ProducerRecord<Long, FastEiendomSomFormuesObjektHendelse>>()

        coEvery {
            producer.send(capture(publisertRecord))
        } returns CompletableDeferred(Unit)

        val resultat = FormuesobjektSyncService(
            formueobjektApi = api,
            messageConsumer = consumer,
            messageProducer = producer,
        ).sync()

        assertThat(resultat)
            .isSuccess()
            .isEqualTo(1)

        assertThat(publisertRecord.captured.key)
            .isEqualTo(matrikkelenhetId)

        assertThat(publisertRecord.captured.value)
            .isEqualTo(
                FastEiendomSomFormuesObjektHendelse(
                    hendelseId = hendelseId.toString(),
                    matrikkelenhetId = matrikkelenhetId,
                    skatteregistrerteEiere = setOf(
                        Serg.SkatteregistrerteEier(
                            identifikator =
                                Serg.Identifikator.Person("01019012345"),
                            eiernivaa = Serg.Eiernivaa.EIENDOMSRETT,
                        ),
                    ),
                ),
            )

        coVerify(exactly = 1) {
            producer.send(any())
        }
        coVerify(exactly = 1) {
            consumer.commitSync()
        }
    }

    @Test
    fun `FFE-005 publiserer ikke melding og committer offset uten retry`() = runTest {
        verifiserForventetApiFeil(
            feilkode = "FFE-005",
            statuskode = 403,
        )
    }

    @Test
    fun `FFE-007 publiserer ikke melding og committer offset uten retry`() = runTest {
        verifiserForventetApiFeil(
            feilkode = "FFE-007",
            statuskode = 404,
        )
    }

    private suspend fun verifiserForventetApiFeil(
        feilkode: String,
        statuskode: Int,
    ) {
        // Arrange: Lag én hendelse som skal behandles.
        val hendelseId = UUID.randomUUID()
        val record = lagRecord(
            matrikkelenhetId = 1001L,
            hendelseId = hendelseId,
        )

        val consumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery {
            consumer.poll(any())
        } returns ConsumerRecords("", listOf(record))
        coEvery {
            consumer.commitSync()
        } returns Unit

        // Skatteetatens API svarer med forventet feil.
        val response = ClientError<Unit>(
            message = "Forventet feil fra Skatteetaten",
            body = """{"kode":"$feilkode"}""",
            statusCode = statuskode,
        )
        val apiFeil = ClientException(
            "Client error: $statuskode $feilkode",
            statuskode,
            response,
        )

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(
                "kartverketMatrikkel",
                hendelseId.toString(),
                any(),
            )
        } throws apiFeil

        val producer =
            mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()

        // Act: Behandle hendelsen.
        val resultat = FormuesobjektSyncService(
            formueobjektApi = api,
            messageConsumer = consumer,
            messageProducer = producer,
        ).sync()

        // Assert: Feilen regnes som forventet.
        assertThat(resultat)
            .isSuccess()
            .isEqualTo(0)

        // API-et skal ikke forsøkes på nytt.
        verify(exactly = 1) {
            api.hentFormuesobjektFastEiendom(
                "kartverketMatrikkel",
                hendelseId.toString(),
                any(),
            )
        }

        // Det finnes ikke et formuesobjekt som kan publiseres.
        coVerify(exactly = 0) {
            producer.send(any())
        }

        // Hendelsen markeres som ferdigbehandlet.
        coVerify(exactly = 1) {
            consumer.commitSync()
        }
    }

    @Test
    fun `committer ikke consumer-offset når Kafka-publisering får timeout`() = runTest {
        val hendelseId = UUID.randomUUID()
        val record = lagRecord(
            matrikkelenhetId = 1001L,
            hendelseId = hendelseId,
        )

        val consumer = mockk<MessageConsumer<Long, Hendelse>>()
        coEvery {
            consumer.poll(any())
        } returns ConsumerRecords("", listOf(record))

        val api = mockk<FormuesobjektFastEiendomApi>()
        every {
            api.hentFormuesobjektFastEiendom(
                "kartverketMatrikkel",
                hendelseId.toString(),
                any(),
            )
        } returns formueobjekt(
            id = 1001L,
            hendelseId = hendelseId,
        )

        val producer =
            mockk<MessageProducer<Long, FastEiendomSomFormuesObjektHendelse>>()

        // Denne fullføres aldri og simulerer at Kafka ikke svarer.
        val sendingSomAldriFullfores = CompletableDeferred<Unit>()

        coEvery {
            producer.send(any())
        } returns sendingSomAldriFullfores

        val resultat = FormuesobjektSyncService(
            formueobjektApi = api,
            messageConsumer = consumer,
            messageProducer = producer,
        ).sync()

        assertThat(resultat)
            .isFailure()
            .isInstanceOf(TimeoutCancellationException::class)

        coVerify(exactly = 1) {
            producer.send(any())
        }
        coVerify(exactly = 0) {
            consumer.commitSync()
        }
    }
}
