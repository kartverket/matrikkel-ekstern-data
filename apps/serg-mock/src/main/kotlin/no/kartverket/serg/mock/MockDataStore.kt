package no.kartverket.serg.mock

import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.validation.V
import no.kartverket.validation.inRange
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class MockDataStore(
    private val maxGenerateBatch: Int = 10_000,
    private val generator: FakeDataGenerator = FakeDataGenerator(),
) {
    private val nextSequence = AtomicLong(1)
    private val lock = ReentrantReadWriteLock()
    private val events = mutableListOf<StoredEvent>()
    private val objectsById = mutableMapOf<String, FastEiendomSomFormuesobjekt>()
    private val eventDateBySequence = mutableMapOf<Long, LocalDate>()

    fun generate(request: GenerateRequest): GenerateSummary {
        val count = V.field("count", request.count)
            .inRange(1..maxGenerateBatch, "must be in range")
            .get()

        return lock.write {
            var firstGenerated: Long? = null
            var lastGenerated: Long? = null

            repeat(count) {
                val sequence = nextSequence.getAndIncrement()
                if (firstGenerated == null) {
                    firstGenerated = sequence
                }
                lastGenerated = sequence

                val row = generator.generate(sequence = sequence, preset = request.preset)
                events += StoredEvent(hendelse = row.hendelse, eventDate = row.eventDate)
                val hendelseId = row.hendelse.hendelseidentifikator?.toString()
                if (hendelseId != null) {
                    objectsById[hendelseId] = row.formuesobjekt
                }
                eventDateBySequence[sequence] = row.eventDate
            }

            GenerateSummary(
                generated = count,
                totalEvents = events.size,
                firstSeq = firstGenerated,
                lastSeq = lastGenerated,
            )
        }
    }

    fun clear(): ClearSummary =
        lock.write {
            val clearedEvents = events.size
            val clearedObjects = objectsById.size
            events.clear()
            objectsById.clear()
            eventDateBySequence.clear()
            nextSequence.set(1)
            ClearSummary(clearedEvents = clearedEvents, clearedObjects = clearedObjects)
        }

    fun readEvents(
        fromSequence: Long,
        limit: Int,
    ): List<Hendelse> =
        lock.read {
            events
                .asSequence()
                .map { it.hendelse }
                .filter { hendelse ->
                    val sequence = hendelse.sekvensnummer ?: Long.MIN_VALUE
                    fromSequence < sequence
                }.take(limit)
                .toList()
        }

    fun findStartSequence(dato: LocalDate?): Long? =
        lock.read {
            if (events.isEmpty()) {
                return@read null
            }
            if (dato == null) {
                return@read events.first().hendelse.sekvensnummer
            }
            eventDateBySequence
                .asSequence()
                .filter { !it.value.isBefore(dato) }
                .map { it.key }
                .minOrNull()
        }

    fun findFormuesobjekt(hendelseidentifikator: String): FastEiendomSomFormuesobjekt? =
        lock.read {
            objectsById[hendelseidentifikator]
        }

    fun snapshot(recentLimit: Int = 20): StoreSnapshot =
        lock.read {
            val firstSeq = events.firstOrNull()?.hendelse?.sekvensnummer
            val lastSeq = events.lastOrNull()?.hendelse?.sekvensnummer

            val recent =
                events.takeLast(recentLimit).asReversed().mapNotNull { stored ->
                    val hendelse = stored.hendelse
                    val seq = hendelse.sekvensnummer ?: return@mapNotNull null
                    val id = hendelse.hendelseidentifikator?.toString() ?: return@mapNotNull null
                    RecentEventView(
                        sekvensnummer = seq,
                        hendelseidentifikator = id,
                        hendelsestype = hendelse.hendelsestype?.value ?: "ukjent",
                        registreringstidspunkt = hendelse.registreringstidspunkt?.toString().orEmpty(),
                        kommunenummer = hendelse.kommunenummer,
                    )
                }

            StoreSnapshot(
                totalEvents = events.size,
                nextSequence = nextSequence.get(),
                firstSeq = firstSeq,
                lastSeq = lastSeq,
                recent = recent,
            )
        }

    fun countEventTypes(): Map<String, Int> =
        lock.read {
            events
                .map { it.hendelse.hendelsestype?.value ?: "ukjent" }
                .groupingBy { it }
                .eachCount()
        }

    private fun validateCount(count: Int) {
        if (count !in 1..maxGenerateBatch) {
            error("count must be between 1 and $maxGenerateBatch")
        }
    }

    private fun validatePositive(
        value: Long,
        field: String,
    ) {
        if (value <= 0) {
            error("$field must be > 0")
        }
    }

    private fun validatePositive(
        value: Int,
        field: String,
    ) {
        if (value <= 0) {
            error("$field must be > 0")
        }
    }

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)
}

private data class StoredEvent(
    val hendelse: Hendelse,
    val eventDate: LocalDate,
)
