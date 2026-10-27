package no.kartverket.matrikkel.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.kartverket.heimdall.common.ktor.plugins.selftest.SelftestGenerator
import no.kartverket.matrikkel.kafkaclient.MessageConsumer
import no.kartverket.matrikkel.kafkaclient.MessageProducer
import kotlin.time.Duration.Companion.seconds

context(scope: CoroutineScope)
fun <TKey, TValue> MessageProducer<TKey, TValue>.withSelftest(
    name: String,
    critical: Boolean
): MessageProducer<TKey, TValue> {
    return SelftestingMessageProducer(this, scope, name, critical)
}

context(scope: CoroutineScope)
fun <TKey, TValue> MessageConsumer<TKey, TValue>.withSelftest(
    name: String,
    critical: Boolean
): MessageConsumer<TKey, TValue> {
    return SelftestingMessageConsumer(this, scope, name, critical)
}

class SelftestingMessageProducer<TKey, TValue>(
    client: MessageProducer<TKey, TValue>,
    private val scope: CoroutineScope,
    name: String,
    critical: Boolean,
) : MessageProducer<TKey, TValue> by client {
    val reporter = SelftestGenerator.Reporter(name, critical)
    init {
        scope.launch {
            while (scope.isActive) {
                reporter.ping {
                    client.metadata()
                }
                delay(30.seconds)
            }
        }
    }
}

class SelftestingMessageConsumer<TKey, TValue>(
    client: MessageConsumer<TKey, TValue>,
    private val scope: CoroutineScope,
    name: String,
    critical: Boolean,
) : MessageConsumer<TKey, TValue> by client {
    val reporter = SelftestGenerator.Reporter(name, critical)

    init {
        scope.launch {
            while (scope.isActive) {
                reporter.ping {
                    client.metadata()
                }
                delay(30.seconds)
            }
        }
    }
}