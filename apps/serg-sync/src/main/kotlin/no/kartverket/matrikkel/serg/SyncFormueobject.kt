package no.kartverket.matrikkel.serg

import no.kartverket.matrikkel.serg.repository.SergDocumentRepository
import no.kartverket.matrikkel.serg.repository.SergDocumentStatus
import no.kartverket.matrikkel.serg.repository.transactional
import no.utgdev.kotlin.retry
import no.utgdev.tjenestespesifikasjoner.serg.formueobjekt.apis.FormuesobjektFastEiendomApi
import java.util.*
import javax.sql.DataSource

class SyncFormueobject(
    private val dataSource: DataSource,
    private val formueobjektApi: FormuesobjektFastEiendomApi,
) {
    private val documentRepository = SergDocumentRepository(dataSource)

    suspend fun sync(): Result<Unit> {
        return runCatching {
            transactional(dataSource) { tx ->
                val documents = documentRepository.listByStatus(tx, SergDocumentStatus.REQUIRE_SYNCHRONIZATION, 10)

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
            }
        }
    }
}