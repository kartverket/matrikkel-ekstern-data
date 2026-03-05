package no.kartverket.matrikkel.serg.formueobjekt

import no.kartverket.kotlin.retry
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.transactional
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import java.util.UUID
import javax.sql.DataSource

class FormuesobjektSyncService(
    private val dataSource: DataSource,
    private val formueobjektApi: FormuesobjektFastEiendomApi,
) {
    private val dokumentRepository = SergDokumentRepository(dataSource)

    suspend fun sync(antall: Int = 10): Result<Int> {
        return runCatching {
            transactional(dataSource) { tx ->
                val dokumenter =
                    dokumentRepository.listEtterStatus(tx, SergDokumentStatus.KREVER_SYNKRONISERING, limit = antall)

                for (dokument in dokumenter) {
                    val hendelseId = dokument.hendelse.hendelseidentifikator
                    val matrikkelenhetId = dokument.matrikkelenhetId
                    if (hendelseId == null) {
                        dokumentRepository.settSomFeil(tx, matrikkelenhetId, "Mangler hendelseId")
                    } else {
                        val result = runCatching {
                            retry(3) {
                                formueobjektApi.hentFormuesobjektFastEiendom(
                                    rettighetspakke = "kartverketMatrikkel",
                                    hendelseidentifikator = hendelseId.toString(),
                                    korrelasjonsid = UUID.randomUUID(),
                                )
                            }
                        }
                        dokumentRepository.settFormueobjektdata(tx, matrikkelenhetId, result)
                    }
                }

                dokumenter.size // Return candidates processed
            }
        }
    }
}