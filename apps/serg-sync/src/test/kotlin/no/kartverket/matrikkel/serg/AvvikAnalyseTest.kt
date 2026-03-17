package no.kartverket.matrikkel.serg

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.runBlocking
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.kartverket.matrikkel.serg.repository.SergDokumentRepository
import no.kartverket.matrikkel.serg.repository.SergDokumentStatus
import no.kartverket.matrikkel.serg.repository.WithDatabase
import no.kartverket.matrikkel.serg.repository.transactional
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.*
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.util.*

class AvvikAnalyseTest : WithDatabase {
    @Test
    fun `tabeller og funksjoner for for avviksanalysen skal eksistere`() {
        assertThat(regclass("eierdiff")).isNotNull()
        assertThat(regclass("eierdiff_filtered")).isNotNull()
        assertThat(functionExists("refresh_eierdiff")).isEqualTo(true)
    }

    @Test
    fun `ingen avvik når person eksisterer i M22 og SERG dokument`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1001L,
            identType = IdentType.Foedselsnummer,
            identValue = "01010112345",
            eiernivaa = Eiernivaa.eiendomsrett,
        )
        insertM22Owner(
            id = 1001L,
            eierforholdkodeid = 18,
            nr = "01010112345",
        )
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "01010112345",
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly()
        assertThat(selectDiff("eierdiff_filtered")).containsExactly()
    }

    @Test
    fun `rapporterer avvik om eier mangler i M22`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1002L,
            identType = IdentType.Foedselsnummer,
            identValue = "02020212345",
            eiernivaa = Eiernivaa.eiendomsrett,
        )
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "02020212345",
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly(
            DiffRow(1002L, "02020212345", 18, "missing_in_matrikkelenhet_eiere")
        )
    }

    @Test
    fun `rapporterer avvik om eier ikke lengre er i SERG men fortsatt er i M22`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1003L,
            identType = IdentType.Foedselsnummer,
            identValue = "03030312345",
            eiernivaa = Eiernivaa.eiendomsrett,
        )
        insertM22Owner(
            id = 1003L,
            eierforholdkodeid = 18,
            nr = "03030312345",
        )
        insertM22Owner(
            id = 1003L,
            eierforholdkodeid = 18,
            nr = "03030399999",
        )
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "03030312345",
        )
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "03030399999",
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly(
            DiffRow(1003L, "03030399999", 18, "extra_in_matrikkelenhet_eiere")
        )
    }

    @Test
    fun `personer med løpenummer er eksplitt ignorert og skaper ikke avvik`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1004L,
            identType = IdentType.Loepenummer,
            identValue = "LP-1004",
            eiernivaa = Eiernivaa.eiendomsrett,
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly()
    }

    @Test
    fun `rader markert som slettet fra SERG skaper ikke avvik`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1005L,
            identType = IdentType.Foedselsnummer,
            identValue = "05050512345",
            eiernivaa = Eiernivaa.eiendomsrett,
            hendelsestype = Hendelsestype.slettet,
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly()
    }

    @Test
    fun `eksplisitt håndtering av eksluderings-regelen for AnnenPerson i eierdiff_filtered`() = runBlocking {
        seedSergDocument(
            matrikkelenhetId = 1006L,
            identType = IdentType.Foedselsnummer,
            identValue = "06060612345",
            eiernivaa = Eiernivaa.eiendomsrett,
        )
        insertPersonIdent(
            klass = "AnnenPerson",
            nr = "06060612345",
        )

        refreshEierdiff()

        assertThat(selectDiff("eierdiff")).containsExactly(
            DiffRow(1006L, "06060612345", 18, "missing_in_matrikkelenhet_eiere")
        )
        assertThat(selectDiff("eierdiff_filtered")).containsExactly()
    }

    private fun regclass(name: String): String? {
        return runInSession {
            run(
                queryOf("SELECT to_regclass(?)", name)
                    .map { it.stringOrNull(1) }
                    .asSingle
            )
        }
    }

    private fun functionExists(name: String): Boolean {
        return runInSession {
            run(
                queryOf("SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname = ?)", name)
                    .map { it.boolean(1) }
                    .asSingle
            )
        } == true
    }

    private fun refreshEierdiff() {
        runInSession {
            run(queryOf("SELECT refresh_eierdiff()").asExecute)
        }
    }

    fun <A> runInSession(fn: Session.() -> A): A {
        return using(sessionOf(dataSource())) { session ->
            fn(session)
        }
    }

    private fun selectDiff(viewName: String): List<DiffRow> {
        return runInSession {
            run(
                queryOf(
                    """
                    SELECT id, nr, eierforholdkodeid, diff_type
                    FROM $viewName
                    ORDER BY id, nr, eierforholdkodeid, diff_type
                    """.trimIndent()
                ).map { row ->
                    DiffRow(
                        id = row.long("id"),
                        nr = row.string("nr"),
                        eierforholdkodeid = row.int("eierforholdkodeid"),
                        diffType = row.string("diff_type"),
                    )
                }.asList
            )
        }
    }

    private fun insertM22Owner(
        id: Long,
        eierforholdkodeid: Int,
        nr: String,
    ) {
        runInSession {
            run(
                queryOf(
                    """
                    INSERT INTO matrikkelenhet_eiere_m22(id, class, eierforholdkodeid, nr)
                    VALUES (?, NULL, ?, ?)
                    """.trimIndent(),
                    id,
                    eierforholdkodeid,
                    nr,
                ).asUpdate
            )
        }
    }

    private fun insertPersonIdent(
        klass: String,
        nr: String,
    ) {
        runInSession {
            run(
                queryOf(
                    "INSERT INTO person_identer_m22(class, nr) VALUES (?, ?)",
                    klass,
                    nr,
                ).asUpdate
            )
        }
    }

    private suspend fun seedSergDocument(
        matrikkelenhetId: Long,
        identType: IdentType,
        identValue: String,
        eiernivaa: Eiernivaa,
        hendelsestype: Hendelsestype = Hendelsestype.ny,
    ) {
        val repository = SergDokumentRepository(dataSource())
        val hendelse = Hendelse(
            sekvensnummer = matrikkelenhetId,
            hendelseidentifikator = UUID.randomUUID(),
            matrikkelUnikIdentifikator = matrikkelenhetId,
            hendelsestype = hendelsestype,
            kommunenummer = "0301",
        )

        repository.upsertFraHendelse(hendelse)
        repository.settFormueobjektdata(
            matrikkelenhetId,
            Result.success(
                FastEiendomSomFormuesobjekt(
                    identifikator = FormuesobjektIdentifikator(
                        matrikkelUnikIdentifikator = matrikkelenhetId,
                    ),
                    hendelsesidentifikator = hendelse.hendelseidentifikator,
                    eieropplysninger = listOf(
                        Eieropplysninger(
                            personidentifikator = identType.personidentifikator(identValue),
                            eierforhold = Eierforhold(eiernivaa = eiernivaa),
                        )
                    ),
                )
            )
        )

        if (hendelsestype == Hendelsestype.slettet) {
            transactional(dataSource()) { tx ->
                repository.settStatus(tx, matrikkelenhetId, SergDokumentStatus.SLETTET)
            }
        }
    }

    private enum class IdentType {
        Foedselsnummer,
        Loepenummer,
        Organisasjonsnummer;

        fun personidentifikator(value: String): Personidentifikator =
            when (this) {
                Foedselsnummer -> Personidentifikator(foedselsnummer = value)
                Loepenummer -> Personidentifikator(loepenummer = value)
                Organisasjonsnummer -> Personidentifikator(organisasjonsnummer = value)
            }
    }

    private data class DiffRow(
        val id: Long,
        val nr: String,
        val eierforholdkodeid: Int,
        val diffType: String,
    )
}