package no.kartverket.matrikkel.serg.formueobjekt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.matrikkel.logger
import no.kartverket.matrikkel.serg.hendelser.HendelserSyncService
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FormueobjektSyncJob(
    private val syncService: FormuesobjektSyncService,
    private val config: Config,
) {
    class Config(
        val antall: Int = 10,
        val interval: Duration = 60.seconds,
    )

    private val probe = SelftestGenerator.Reporter("FormueobjektSyncJob", critical = false)

    suspend fun start() {
        val ctx = currentCoroutineContext()
        while (ctx.isActive) {
            val hendelser: Result<Int> = syncService.sync(config.antall)
            val antallHentet = hendelser
                .fold(
                    onFailure = {
                        if (it is CancellationException) throw it
                        probe.reportError(it)
                        logger.error("Feilet med henting av formueobjekt", it)
                        0
                    },
                    onSuccess = {
                        probe.reportOk()
                        logger.info("Hentet $it formueobjekt fra SERG")
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
