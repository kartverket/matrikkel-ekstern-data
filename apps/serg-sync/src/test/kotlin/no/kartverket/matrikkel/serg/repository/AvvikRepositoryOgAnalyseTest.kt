package no.kartverket.matrikkel.serg.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toJavaLocalDateTime
import kotliquery.Session
import kotliquery.queryOf
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.*
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelsestype
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class AvvikRepositoryOgAnalyseTest : WithDatabase {
    @Test
    fun `ingen avvik når person eksisterer i M22 og SERG dokument`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1001L,
            eier = fnr("01010112345") som Eiernivaa.eiendomsrett,
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

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly()
    }

    @Test
    fun `rapporterer avvik om eier mangler i M22`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1002L,
            eier = fnr("02020212345") som Eiernivaa.eiendomsrett,
        )
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "02020212345",
        )

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly(
            AvvikRepository.Avvik(
                matrikkelenhetId = 1002L,
                nr = "02020212345",
                eierforholdKodeId = 18,
                type = AvvikRepository.AvvikType.MANGLER_I_M22
            )
        )
    }

    @Test
    fun `rapporterer ikke avvik om endringen er forårsaket av nylig endring i SERG`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1002L,
            eier = fnr("02020212345") som Eiernivaa.eiendomsrett,
        )
        settSistOppdatertForSergDokument(1002L, LocalDateTime.now())
        insertPersonIdent(
            klass = "FysiskPerson",
            nr = "02020212345",
        )

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).isEmpty()
    }

    @Test
    fun `rapporterer avvik om eier ikke lengre er i SERG men fortsatt er i M22`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1003L,
            eier = fnr("03030312345") som Eiernivaa.eiendomsrett,
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

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly(
            AvvikRepository.Avvik(
                matrikkelenhetId = 1003L,
                nr = "03030399999",
                eierforholdKodeId = 18,
                type = AvvikRepository.AvvikType.EKSTRA_I_M22
            )
        )
    }

    @Test
    fun `personer med løpenummer er eksplitt ignorert og skaper ikke avvik`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1004L,
            eier = lopenr("LP-1004") som Eiernivaa.eiendomsrett,
        )

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly()
    }

    @Test
    fun `rader markert som slettet fra SERG skaper ikke avvik`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1005L,
            eier = fnr("05050512345") som Eiernivaa.eiendomsrett,
            hendelsestype = Hendelsestype.slettet,
        )

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly()
    }

    @Test
    fun `eksplisitt håndtering av eksluderings-regelen for AnnenPerson i avvik`() = runBlocking {
        val repository = AvvikRepository(dataSource(), dataSource())

        seedSergDocument(
            matrikkelenhetId = 1006L,
            eier = fnr("06060612345") som Eiernivaa.eiendomsrett,
        )
        insertPersonIdent(
            klass = "AnnenPerson",
            nr = "06060612345",
        )

        repository.oppdaterAvvik()

        assertThat(repository.hentAvvik()).containsExactly()
    }

    fun <A> runInSession(fn: Session.() -> A): A {
        return dataSource().runSql(fn)
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

    private infix fun Personidentifikator.som(type: Eiernivaa) = Eieropplysninger(
        personidentifikator = this,
        erTinglyst = null,
        eierforhold = Eierforhold(
            eiernivaa = type
        )
    )

    private suspend fun seedSergDocument(
        matrikkelenhetId: Long,
        eier: Eieropplysninger,
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
                    eieropplysninger = listOf(eier),
                )
            )
        )
        settSistOppdatertForSergDokument(matrikkelenhetId, LocalDateTime.now().minusDays(2))

        if (hendelsestype == Hendelsestype.slettet) {
            dataSource().withTransaction { tx ->
                repository.settStatus(tx, matrikkelenhetId, SergDokumentStatus.SLETTET)
            }
        }
    }

    fun settSistOppdatertForSergDokument(
        matrikkelenhetId: Long,
        sistOppdatert: LocalDateTime,
    ) {
        dataSource().runSql {
            val query = queryOf(
                """
                UPDATE serg_dokument 
                SET sistOppdatert = :sistOppdatert
                WHERE matrikkelenhetId = :matrikkelenhetId
            """.trimIndent(), mapOf(
                    "matrikkelenhetId" to matrikkelenhetId,
                    "sistOppdatert" to sistOppdatert,
                )
            ).asExecute
            it.run(query)
        }
    }

    private fun fnr(value: String) = Personidentifikator(foedselsnummer = value)
    private fun dnr(value: String) = Personidentifikator(dNummer = value)
    private fun lopenr(value: String) = Personidentifikator(loepenummer = value)
    private fun orgnr(value: String) = Personidentifikator(organisasjonsnummer = value)
}