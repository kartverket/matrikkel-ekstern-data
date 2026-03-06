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
            keyValueRepository.getValueOrNull(sekvensnummerKey)?.toLong() ?: 1
        }.mapCatching { sekvensnummer ->
            retry(3) {
                hendelserApi.hentHendelserFormuesobjektFastEiendom(
                    fraSekvensnummer = sekvensnummer,
                    antall = antall,
                    korrelasjonsid = UUID.randomUUID(),
                )
            }
        }.mapCatching { result ->
            val hendelser = result.hendelser ?: emptyList()
            // Lagrer hendelsene først for å sikre at disse er lagret før dokumentRepo
            transactional(dataSource) { tx ->
                for (hendelse in hendelser) {
                    hendelseRepository.insert(tx, hendelse)
                }
            }
            transactional(dataSource) { tx ->
                for (hendelse in hendelser) {
                    val hendelseId = "${hendelse.sekvensnummer}/${hendelse.hendelseidentifikator}"
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
            }
            hendelser
        }.map { hendelser ->
            val maxSekvensnummer = hendelser.maxOfOrNull { it.sekvensnummer ?: -1 } ?: -1
            if (maxSekvensnummer > -1) {
                keyValueRepository.setValue(sekvensnummerKey, maxSekvensnummer.toString())
            }
            hendelser
        }
    }
}
