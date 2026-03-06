package no.kartverket.matrikkel.serg.hendelser

import kotlinx.coroutines.runBlocking
import no.kartverket.kotlin.SelftestGenerator
import no.kartverket.kotlin.retry
import no.kartverket.matrikkel.logger
import no.kartverket.matrikkel.serg.repository.HendelseRepository
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.transactional
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import java.util.*
import javax.sql.DataSource

class HendelserSyncService(
    private val dataSource: DataSource,
    private val hendelserApi: HendelserApi,
) {
    private val sekvensnummerKey = "sekvensnummer"
    private val keyValueRepository = KeyValueRepository(dataSource)
    private val dokumentRepository = SergDokumentRepository(dataSource)
    private val hendelseRepository = HendelseRepository(dataSource)

    init {
        SelftestGenerator.Metadata(sekvensnummerKey) {
            runBlocking {
                keyValueRepository.getValue(sekvensnummerKey)
            }
        }
    }

    suspend fun sync(antall: Int = 1000): Result<List<Hendelse>> {
        return runCatching {
            transactional(dataSource) { tx ->
                val sekvensnummer = keyValueRepository.getValueOrNull(tx, sekvensnummerKey)?.toLong() ?: 1
                val hendelser = retry(3) {
                    hendelserApi.hentHendelserFormuesobjektFastEiendom(
                        fraSekvensnummer = sekvensnummer,
                        antall = antall,
                        korrelasjonsid = UUID.randomUUID(),
                    )
                }.hendelser ?: emptyList()

                for (hendelse in hendelser) {
                    val hendelseId = "${hendelse.sekvensnummer}/${hendelse.hendelseidentifikator}"
                    hendelseRepository.insert(tx, hendelse)
                    try {
                        if (hendelse.matrikkelUnikIdentifikator == null) {
                            logger.warn("Ignorerer hendelse: ${hendelseId}. Manglet matrikkelUnikIdentifikator")
                        } else if (hendelse.hendelsestype == null) {
                            logger.warn("Ignorerer hendelse: ${hendelseId}. Manglet hendelsetype")
                        } else {
                            dokumentRepository.upsertFraHendelse(tx, hendelse)
                        }
                    } catch (e: IllegalStateException) {
                        logger.error(
                            "Kunne ikke lagre hendelse: ${hendelse.sekvensnummer}/${hendelse.hendelseidentifikator}",
                            e,
                        )
                    }
                }

                val maxSekvensnummer = hendelser.maxOfOrNull { it.sekvensnummer ?: -1 } ?: -1
                if (maxSekvensnummer > -1) {
                    keyValueRepository.setValue(tx, sekvensnummerKey, maxSekvensnummer.toString())
                }

                hendelser
            }
        }
    }
}
