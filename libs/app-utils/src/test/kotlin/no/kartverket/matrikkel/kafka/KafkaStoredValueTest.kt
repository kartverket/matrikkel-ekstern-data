package no.kartverket.matrikkel.kafka

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.kartverket.matrikkel.kafkaclient.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class KafkaStoredValueTest {
    @Test
    fun `skal laste ned all data før den settes som ready`() = runBlocking {
        val simulator = KafkaSimulator()

        simulator.existingRecords(
            ProducerRecord("update", 1L),
            ProducerRecord("update", 2L),
            ProducerRecord("update", 3L),
            ProducerRecord("update", 4L),
        )
        simulator.existingRecords(
            ProducerRecord("update", 5L),
            ProducerRecord("update", 6L),
            ProducerRecord("update", 7L),
            ProducerRecord("update", 123L),
        )

        val sync = KafkaStoredValue(simulator.consumer, simulator.producer)
        sync.start()
        sync.waitToReady(5.seconds)

        val syncValue = sync.getValue()

        sync.setValue(900L)
        val localUpdatedValue = sync.getValue()

        sync.close()

        assertThat(syncValue).isEqualTo(123L)
        assertThat(localUpdatedValue).isEqualTo(900L)
        coVerify(exactly = 1) { simulator.producer.send(ProducerRecord("update", 900L)) }
    }
}

class KafkaSimulator {
    val consumer = mockk<MessageConsumer<String, Long>>()
    val producer = mockk<MessageProducer<String, Long>>()
    private val currentSequence = AtomicLong(1L)
    private val recordBatches = ConcurrentLinkedQueue<ConsumerRecords<String, Long>>()

    init {
        coEvery { consumer.close() } returns Unit
        coEvery { producer.close() } returns Unit

        coEvery { consumer.poll(maxRecords = any(), timeout = any()) } answers {
            if (recordBatches.isEmpty()) {
                ConsumerRecords(topic = "topic", records = emptyList())
            } else {
                recordBatches.poll()
            }
        }

        coEvery { producer.send(any()) } answers { call ->
            val record = call.invocation.args.first() as ProducerRecord<String, Long>
            recordBatches.offer(
                ConsumerRecords(
                    topic = "topic",
                    records = listOf(record.asConsumerRecord()),
                )
            )
            CompletableDeferred(Unit)
        }
    }

    fun existingRecords(vararg record: ProducerRecord<String, Long>) {
        existingRecords(record.asList())
    }

    fun existingRecords(records: List<ProducerRecord<String, Long>>) {
        recordBatches.offer(
            ConsumerRecords(
                topic = "topic",
                records = records.map { it.asConsumerRecord() }
            )
        )
    }

    private fun ProducerRecord<String, Long>.asConsumerRecord(): ConsumerRecord<String, Long> {
        return ConsumerRecord(
            topic = "topic",
            sequence = currentSequence.incrementAndGet(),
            key = this.key,
            value = this.value,
            publishedAt = Clock.System.now(),
        )

    }
}