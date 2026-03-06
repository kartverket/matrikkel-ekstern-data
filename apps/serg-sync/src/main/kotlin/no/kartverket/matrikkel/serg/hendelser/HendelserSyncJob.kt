package no.kartverket.matrikkel.serg.hendelser

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.matrikkel.logger
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HendelserSyncJob(
    private val syncService: HendelserSyncService,
    private val config: Config,
) {
    class Config(
        val antall: Int = 1000,
        val interval: Duration = 60.seconds,
    )


    suspend fun start() {
        val probe = SelftestGenerator.Reporter("HendelserSyncJob", critical = false)
        val ctx = currentCoroutineContext()
        while (ctx.isActive) {
            val hendelser: Result<List<Hendelse>> = syncService.sync(config.antall)
            val antallHentet = hendelser
                .map { it.size }
                .fold(
                    onFailure = {
                        if (it is CancellationException) throw it
                        probe.reportError(it)
                        logger.error("Feilet med henting av hendelser", it)
                        0
                    },
                    onSuccess = {
                        probe.reportOk()
                        logger.info("Hentet $it hendelser fra SERG")
                        it
                    },
                )

            if (antallHentet != config.antall) {
                delay(config.interval)
            } else {
                yield() //
            }
        }
    }
}
