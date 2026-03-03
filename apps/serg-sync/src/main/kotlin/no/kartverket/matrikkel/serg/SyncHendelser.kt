package no.kartverket.matrikkel.serg

import no.kartverket.matrikkel.logger
import no.kartverket.matrikkel.serg.repository.KeyValueRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentRepository
import no.kartverket.matrikkel.serg.repository.transactional
import no.utgdev.kotlin.retry
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.apis.HendelserApi
import no.utgdev.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import java.util.*
import javax.sql.DataSource

class SyncHendelser(
    private val dataSource: DataSource,
    private val hendelserApi: HendelserApi,
) {
    private val sekvensnummerKey = "sekvensnummer"
    private val keyValueRepository = KeyValueRepository(dataSource)
    private val documentRepository = SergDocumentRepository(dataSource)

    suspend fun sync(antall: Int = 1000): Result<List<Hendelse>> {
        return runCatching {
            keyValueRepository.getValueOrNull(sekvensnummerKey)?.toLong() ?: 1
        }.mapCatching { sekvensnummer ->
            retry(3) {
                hendelserApi.hentHendelserFormuesobjektFastEiendom(
                    fraSekvensnummer = sekvensnummer,
                    antall = antall,
                    korrelasjonsid = UUID.randomUUID()
                )
            }
        }.mapCatching { result ->
            val hendelser = result.hendelser ?: emptyList()
            transactional(dataSource) { tx ->
                for (hendelse in hendelser) {
                    try {
                        documentRepository.upsertFraHendelse(tx, hendelse)
                    } catch (e: IllegalStateException) {
                        logger.error("Kunne ikke lagre hendelse: ${hendelse.sekvensnummer}/${hendelse.hendelseidentifikator}", e)
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