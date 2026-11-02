package no.kartverket.matrikkel.kafka

import kotlinx.coroutines.*
import no.kartverket.matrikkel.kafkaclient.*
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.updateAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalAtomicApi::class)
class KafkaStoredValue<T : Comparable<T>>(
    private val consumer: MessageConsumer<String, T>,
    private val producer: MessageProducer<String, T>,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(KafkaStoredValue::class.java)
    private val ready = CompletableDeferred<Unit>()
    private val localCopy: AtomicReference<T?> = AtomicReference(null)
    private var job: Job? = null

    constructor(
        config: KafkaConfiguration,
        topic: String,
        serde: Serde<T>,
        authentication: ClientAuthentication?,
    ) : this(
        MessageConsumer.Impl(
            MessageConsumer.Config(
                server = config.brokerUrl,
                authentication = authentication,
                topic = topic,
                keySerializer = StringSerde,
                valueSerializer = serde,
                correlationIdProvider = { Uuid.random().toHexDashString() },
                consumerGroup = topic,
                instanceId = Uuid.random().toHexDashString(),
                initialOffsetPolicy = InitialOffsetPolicy.EARLIEST,
            )
        ),
        MessageProducer.Impl(
            MessageProducer.Config(
                server = config.brokerUrl,
                authentication = authentication,
                topic = topic,
                keySerializer = StringSerde,
                valueSerializer = serde,
                correlationIdProvider = { Uuid.random().toHexDashString() },
                bufferSize = 1,
                linger = 10.milliseconds,
            )
        )
    )

    context(scope: CoroutineScope)
    fun start() {
        if (job != null) error("Already started")

        job = scope.launch {
            while (isActive) {
                runCatching {
                    val response = consumer.poll(timeout = 10.seconds)

                    if (response.records.isEmpty()) {
                        ready.complete(Unit)
                        delay(10.seconds)
                    } else {
                        val newValue = requireNotNull(response.records.last().value)
                        localCopy.setMax(newValue)
                    }
                }
                    .onFailure { exception ->
                        if (exception is CancellationException) throw exception
                        logger.warn("Error polling distributed values", exception)
                        delay(1.seconds)
                    }
            }
        }
    }

    override fun close() {
        runBlocking {
            job?.cancelAndJoin()
        }
        consumer.close()
        producer.close()
    }

    fun isReady(): Boolean {
        return ready.isCompleted
    }

    fun waitToReady(timeout: Duration = 15.seconds) {
        runBlocking {
            withTimeout(timeout) {
                ready.await()
            }
        }
    }

    fun getValue(): T? {
        waitToReady(15.seconds)
        return localCopy.load()
    }

    fun setValue(value: T) {
        localCopy.setMax(value)
        runBlocking {
            producer.send(
                ProducerRecord(
                    key = "update",
                    value = value
                )
            ).await()
        }
    }

    private fun AtomicReference<T?>.setMax(value: T) {
        updateAndFetch { current ->
            if (current == null || value > current) value else current
        }
    }
}