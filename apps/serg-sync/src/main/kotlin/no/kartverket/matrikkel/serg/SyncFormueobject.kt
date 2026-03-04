package no.kartverket.matrikkel.serg

import no.kartverket.matrikkel.serg.repository.SergDocumentRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentStatus
import no.kartverket.matrikkel.serg.repository.transactional
import no.kartverket.kotlin.retry
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import java.util.*
import javax.sql.DataSource

class SyncFormueobject(
    private val dataSource: DataSource,
    private val formueobjektApi: FormuesobjektFastEiendomApi,
) {
    private val documentRepository = SergDocumentRepository(dataSource)

    suspend fun sync(antall: Int = 10): Result<Int> {
        return runCatching {
            transactional(dataSource) { tx ->
                val documents = documentRepository.listByStatus(tx, SergDocumentStatus.REQUIRE_SYNCHRONIZATION, limit = antall)

                for (document in documents) {
                    val hendelseId = document.hendelse.hendelseidentifikator
                    val matrikkelenhetId = document.matrikkelenhetId
                    if (hendelseId == null) {
                        documentRepository.settSomFeil(tx, matrikkelenhetId, "Mangler hendelseId")
                    } else {
                        val result = runCatching {
                            retry(3) {
                                formueobjektApi.hentFormuesobjektFastEiendom(
                                    rettighetspakke = "kartverketMatrikkel",
                                    hendelseidentifikator = hendelseId.toString(),
                                    korrelasjonsid = UUID.randomUUID()
                                )
                            }
                        }
                        documentRepository.settFormueobjektdata(tx, matrikkelenhetId, result)
                    }
                }

                documents.size // Return candidates processed
            }
        }
    }
}