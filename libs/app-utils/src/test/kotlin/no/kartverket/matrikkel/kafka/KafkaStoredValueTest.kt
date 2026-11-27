package no.kartverket.matrikkel.kafka

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import no.kartverket.matrikkel.kafkaclient.*
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class KafkaStoredValueTest {
    @Test
    fun `skal laste ned all data før den settes som ready`(): Unit = runTest {
        val simulator = KafkaSimulator()
        val dispatcher = StandardTestDispatcher(testScheduler)

        simulator.existingRecords(
            ProducerRecord("update", 1L),
            ProducerRecord("update", 2L),
            ProducerRecord("update", 3L),
            ProducerRecord("other", 3L),
            ProducerRecord("update", 4L),
        )
        simulator.existingRecords(
            ProducerRecord("update", 5L),
            ProducerRecord("update", 6L),
            ProducerRecord("other", 456L),
            ProducerRecord("update", 7L),
            ProducerRecord("update", 123L),
        )

        val sync = KafkaStoredValue(simulator.consumer, simulator.producer, dispatcher)
        sync.start()
        sync.waitToReady(15.seconds)

        val syncValue = sync.getValue()
        val otherValue = sync.getValue("other")

        sync.setValue(900L)
        sync.setValue("other", 999L)
        val localUpdatedValue = sync.getValue()
        val otherLocalUpdatedValue = sync.getValue("other")

        sync.stop()

        assertThat(syncValue).isEqualTo(123L)
        assertThat(otherValue).isEqualTo(456L)
        assertThat(localUpdatedValue).isEqualTo(900L)
        assertThat(otherLocalUpdatedValue).isEqualTo(999L)
        coVerify(exactly = 1) { simulator.producer.send(ProducerRecord("update", 900L)) }
        coVerify(exactly = 1) { simulator.producer.send(ProducerRecord("other", 999L)) }
    }

    @Test
    fun `starter opp selv når det ikke eksisterer en tidligere verdi`(): Unit = runTest {
        val simulator = KafkaSimulator()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val sync = KafkaStoredValue(simulator.consumer, simulator.producer, dispatcher)
        sync.start()
        sync.waitToReady(15.seconds)

        assertThat(sync.getValue()).isNull()

        sync.stop()
    }

    @Test
    fun `restart skal hente opp tidligere lagret verdi`(): Unit = runTest {
        val simulator = KafkaSimulator()
        val dispatcher = StandardTestDispatcher(testScheduler)

        simulator.existingRecords(
            ProducerRecord("update", 1L),
            ProducerRecord("update", 2L),
            ProducerRecord("update", 3L),
            ProducerRecord("update", 4L),
        )

        val sync1 = KafkaStoredValue(simulator.consumer, simulator.producer, dispatcher)
        sync1.start()
        sync1.waitToReady(5.seconds)
        val value1 = sync1.getValue()
        sync1.stop()

        simulator.simulateSeekErrorsNTimes(3)

        val secondSync = KafkaStoredValue(simulator.consumer, simulator.producer, dispatcher)
        secondSync.start()
        secondSync.waitToReady(60.seconds)
        val value2 = secondSync.getValue()
        secondSync.stop()

        assertThat(value1).isEqualTo(4L)
        assertThat(value2).isEqualTo(4L)
    }
}

class KafkaSimulator {
    val consumer = mockk<MessageConsumer<String, Long>>()
    val producer = mockk<MessageProducer<String, Long>>()
    private var currentSequence = 1
    private var currentOffset = 0
    private val topic = mutableListOf<ConsumerRecord<String, Long>>()
    private var seekError = 0

    init {
        coEvery { consumer.close() } returns Unit
        coEvery { producer.close() } returns Unit

        coEvery { consumer.poll(maxRecords = any(), timeout = any()) } answers {
            val maxRecords = (it.invocation.args.firstOrNull() as Long?)?.toInt() ?: 100
            if (topic.isEmpty()) {
                ConsumerRecords(topic = "topic", records = emptyList())
            } else {
                val records = topic.subList(currentOffset, minOf(currentOffset + maxRecords, topic.size))
                currentOffset = minOf(currentOffset + maxRecords, topic.size)
                ConsumerRecords(topic = "topic", records)
            }
        }

        coEvery { consumer.seek(any<Long>()) } answers {
            val offset = it.invocation.args.first() as Long
            if (seekError-- > 0) {
                throw KafkaClientException(
                    status = HttpStatusCode.BadRequest,
                    code = "badRequest",
                    message = "A current lease prevents seeking for this consumer group",
                    cause = RuntimeException()
                )
            }
            currentOffset = offset.toInt()
        }

        coEvery { producer.send(any()) } answers { call ->
            val record = call.invocation.args.first() as ProducerRecord<String, Long>
            topic.add(record.asConsumerRecord())
            CompletableDeferred(Unit)
        }
    }

    fun simulateSeekErrorsNTimes(n: Int) {
        seekError = n
    }

    fun existingRecords(vararg record: ProducerRecord<String, Long>) {
        existingRecords(record.asList())
    }

    fun existingRecords(records: List<ProducerRecord<String, Long>>) {
        topic.addAll(
            records.map { it.asConsumerRecord() }
        )
    }

    private fun ProducerRecord<String, Long>.asConsumerRecord(): ConsumerRecord<String, Long> {
        return ConsumerRecord(
            topic = "topic",
            sequence = (currentSequence++).toLong(),
            key = this.key,
            value = this.value,
            publishedAt = Clock.System.now(),
        )
    }
}