package no.kartverket.matrikkel.kafka

import kotlinx.coroutines.*
import no.kartverket.matrikkel.kafkaclient.*
import no.kartverket.matrikkel.resilience.BackoffStrategy
import no.kartverket.matrikkel.resilience.retry
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class KafkaStoredValue<T : Comparable<T>>(
    private val consumer: MessageConsumer<String, T>,
    private val producer: MessageProducer<String, T>,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(KafkaStoredValue::class.java)
    private val ready = CompletableDeferred<Unit>()
    private val localCopies = ConcurrentHashMap<String, T>()
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

        job = scope.launch(workerDispatcher) {
            // Seek er kun mulig når ingen lease's er aktive, dvs tidligere konsument har koblet seg fra.
            // Denne venter maks 5minutter for at dette skal skje.
            try {
                retry(
                    attempts = 20,
                    backoffStrategy = BackoffStrategy.Constant(15.seconds),
                    onFailure = { attempt, exception -> logger.warn("Seek feilet (forsøk $attempt/20)", exception) },
                ) {
                    consumer.seekOrThrow(0L)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Gir årsaken videre til waitToReady i stedet for å kansellere parent-scope
                ready.completeExceptionally(e)
                return@launch
            }
            while (isActive) {
                runCatching {
                    val response = consumer.pollOrThrow(timeout = 10.seconds)

                    if (response.records.isEmpty()) {
                        ready.complete(Unit)
                        delay(10.seconds)
                    } else {
                        for (record in response.records) {
                            val newValue = requireNotNull(record.value)
                            localCopies.setMax(record.key, newValue)
                        }
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

    suspend fun stop() {
        job?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            consumer.close()
            producer.close()
        }
    }

    override fun close() {
        runBlocking {
            stop()
        }
    }

    fun isReady(): Boolean {
        return ready.isCompleted && !ready.isCancelled
    }

    suspend fun waitToReady(timeout: Duration = 15.seconds) {
        withTimeout(timeout) {
            ready.await()
        }
    }

    suspend fun computeIfAbsent(key: String = "update", fn: () -> T): T {
        waitToReady(15.seconds)
        return localCopies.computeIfAbsent(key) {
            fn()
        }
    }

    suspend fun getValue(key: String = "update"): T? {
        waitToReady(15.seconds)
        return localCopies[key]
    }

    suspend fun setValue(value: T) {
        setValue(key = "update", value = value)
    }

    suspend fun setValue(key: String, value: T) {
        localCopies.setMax(key, value)
        producer.send(
            ProducerRecord(
                key = key,
                value = value
            )
        ).await()
    }

    private fun ConcurrentHashMap<String, T>.setMax(key: String, value: T) {
        compute(key) { _, current ->
            if (current == null || value > current) value else current
        }
    }
}