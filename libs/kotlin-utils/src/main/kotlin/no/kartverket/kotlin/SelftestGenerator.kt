package no.kartverket.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SelftestGenerator private constructor(
    private var config: Config,
) {
    companion object {
        private var instance = SelftestGenerator(Config())

        fun getInstance(config: Config): SelftestGenerator {
            instance.config = config
            return instance
        }
    }

    open class Config(
        var appname: String = "Not set",
        var version: String = "Not set",
    )

    sealed class Event(
        val reporter: Reporter,
    )

    class InitEvent(
        reporter: Reporter,
    ) : Event(reporter)

    class OkEvent(
        reporter: Reporter,
    ) : Event(reporter)

    class ErrorEvent(
        reporter: Reporter,
        val error: Throwable,
    ) : Event(reporter)

    private val statusmap = mutableMapOf<String, Event>()
    private val metadatamap = mutableMapOf<String, Metadata>()

    internal fun register(event: Event) {
        statusmap[event.reporter.name] = event
    }

    internal fun register(metadata: Metadata) {
        metadatamap[metadata.name] = metadata
    }

    internal fun clear() {
        statusmap.clear()
        metadatamap.clear()
    }

    fun isAlive(): Boolean = statusmap.values.none { it is ErrorEvent && it.reporter.critical }

    fun isReady(): Boolean {
        val isReady = statusmap.values.all { it !is InitEvent }
        val noCriticalError = statusmap.values.none { it is ErrorEvent && it.reporter.critical }
        return isReady && noCriticalError
    }

    fun scrape() =
        buildString {
            appendLine("Appname: ${config.appname}")
            appendLine("Version: ${config.version}")
            appendLine()
            appendLine("Status:")
            for (result in statusmap.values) {
                val critical = if (result.reporter.critical) "(Critical)" else ""
                val status =
                    when (result) {
                        is InitEvent -> "Registered"
                        is OkEvent -> "OK"
                        is ErrorEvent -> "KO: ${result.error.message}"
                    }
                appendLine("\tName: ${result.reporter.name} $critical Status: $status")
            }
            if (metadatamap.isNotEmpty()) {
                appendLine()
                appendLine("Metadata:")
                for (metadata in metadatamap.values) {
                    appendLine("\tName: ${metadata.name} Value: ${metadata.fn()}")
                }
            }
        }

    class Reporter(
        val name: String,
        val critical: Boolean,
    ) {
        init {
            instance.register(InitEvent(this))
        }

        fun reportOk() {
            instance.register(OkEvent(this))
        }

        fun reportError(error: Throwable) {
            instance.register(ErrorEvent(this, error))
        }

        fun ping(fn: suspend () -> Unit) {
            try {
                runBlocking(Dispatchers.IO) {
                    fn()
                }
                reportOk()
            } catch (e: Throwable) {
                reportError(e)
            }
        }
    }

    class Metadata(
        val name: String,
        val fn: () -> String,
    ) {
        init {
            instance.register(this)
        }
    }
}