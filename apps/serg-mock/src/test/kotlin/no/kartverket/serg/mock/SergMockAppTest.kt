package no.kartverket.serg.mock

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelser
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.Feil as FormueFeil
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Feil as HendelserFeil

class SergMockAppTest {
    @Test
    fun `get hendelser returns empty list initially`() =
        testApplication {
            configureMockApp()

            val response = client.get("/hendelser")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)

            val body = SergMockJson.decodeFromString<Hendelser>(response.bodyAsText())
            assertThat(body.hendelser).isEqualTo(emptyList())
        }

    @Test
    fun `hendelser validates fraSekvensnummer and antall when provided`() =
        testApplication {
            configureMockApp()

            val invalidSequence = client.get("/hendelser?fraSekvensnummer=abc")
            assertThat(invalidSequence.status).isEqualTo(HttpStatusCode.BadRequest)
            val sequenceError = SergMockJson.decodeFromString<HendelserFeil>(invalidSequence.bodyAsText())
            assertThat(sequenceError.kode).isEqualTo("MOCK-400")
            assertThat(sequenceError.melding).isEqualTo("fraSekvensnummer must be a valid integer")

            val invalidCount = client.get("/hendelser?antall=-1")
            assertThat(invalidCount.status).isEqualTo(HttpStatusCode.BadRequest)
            val countError = SergMockJson.decodeFromString<HendelserFeil>(invalidCount.bodyAsText())
            assertThat(countError.kode).isEqualTo("MOCK-400")
            assertThat(countError.melding).isEqualTo("antall must be > 0")
        }

    @Test
    fun `hendelser start validates date format and rejects future dates`() =
        testApplication {
            configureMockApp()

            val invalidDate = client.get("/hendelser/start?dato=2024/01/01")
            assertThat(invalidDate.status).isEqualTo(HttpStatusCode.BadRequest)
            val invalidDateError = SergMockJson.decodeFromString<HendelserFeil>(invalidDate.bodyAsText())
            assertThat(invalidDateError.melding).isEqualTo("dato must use ISO format YYYY-MM-DD")

            val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
            val futureDate = client.get("/hendelser/start?dato=$tomorrow")
            assertThat(futureDate.status).isEqualTo(HttpStatusCode.BadRequest)
            val futureDateError = SergMockJson.decodeFromString<HendelserFeil>(futureDate.bodyAsText())
            assertThat(futureDateError.melding).isEqualTo("dato cannot be in the future")
        }

    @Test
    fun `admin generate populates hendelser in ascending order`() =
        testApplication {
            configureMockApp()

            val generateResponse =
                client.post("/admin/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"count":3,"preset":"BALANCED"}""")
                }
            assertThat(generateResponse.status).isEqualTo(HttpStatusCode.OK)

            val eventsResponse = client.get("/hendelser?fraSekvensnummer=1&antall=3")
            val body = SergMockJson.decodeFromString<Hendelser>(eventsResponse.bodyAsText())
            val sequences = body.hendelser.orEmpty().mapNotNull { it.sekvensnummer }
            assertThat(sequences).isEqualTo(listOf(1L, 2L, 3L))
        }

    @Test
    fun `v1 routes mirror root routes`() =
        testApplication {
            configureMockApp()

            client.post("/admin/api/generate") {
                contentType(ContentType.Application.Json)
                setBody("""{"count":2,"preset":"BALANCED"}""")
            }

            val root = client.get("/hendelser?fraSekvensnummer=1&antall=2")
            val versioned = client.get("/v1/hendelser?fraSekvensnummer=1&antall=2")

            assertThat(root.status).isEqualTo(HttpStatusCode.OK)
            assertThat(versioned.status).isEqualTo(HttpStatusCode.OK)
            assertThat(root.bodyAsText()).isEqualTo(versioned.bodyAsText())
        }

    @Test
    fun `formuesobjekt endpoint returns mapped object for generated hendelse`() =
        testApplication {
            configureMockApp()

            client.post("/admin/api/generate") {
                contentType(ContentType.Application.Json)
                setBody("""{"count":1,"preset":"BALANCED"}""")
            }

            val hendelser = SergMockJson.decodeFromString<Hendelser>(client.get("/hendelser").bodyAsText())
            val hendelseId =
                hendelser.hendelser
                    .orEmpty()
                    .first()
                    .hendelseidentifikator
                    .toString()

            val response = client.get("/testpakke/$hendelseId")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)

            val formuesobjekt = SergMockJson.decodeFromString<FastEiendomSomFormuesobjekt>(response.bodyAsText())
            assertThat(formuesobjekt.hendelsesidentifikator.toString()).isEqualTo(hendelseId)
        }

    @Test
    fun `unknown hendelseidentifikator returns 404 error payload`() =
        testApplication {
            configureMockApp()

            val response = client.get("/testpakke/00000000-0000-0000-0000-000000000000")
            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)

            val feil = SergMockJson.decodeFromString<FormueFeil>(response.bodyAsText())
            assertThat(feil.kode).isEqualTo("MOCK-404")
            assertThat(feil.melding).isEqualTo("Fant ikke hendelseidentifikator")
            assertThat(feil.korrelasjonsid).isNotNull()
        }

    @Test
    fun `admin generate rejects count outside accepted range`() =
        testApplication {
            configureMockApp()

            val tooSmall =
                client.post("/admin/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"count":0,"preset":"BALANCED"}""")
                }
            assertThat(tooSmall.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(tooSmall.bodyAsText().contains("between 1 and 10000"))

            val tooLarge =
                client.post("/admin/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"count":10001,"preset":"BALANCED"}""")
                }
            assertThat(tooLarge.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(tooLarge.bodyAsText().contains("between 1 and 10000"))
        }

    @Test
    fun `clear endpoint removes all generated data`() =
        testApplication {
            configureMockApp()

            client.post("/admin/api/generate") {
                contentType(ContentType.Application.Json)
                setBody("""{"count":5,"preset":"BALANCED"}""")
            }

            val clear = client.post("/admin/api/clear")
            assertThat(clear.status).isEqualTo(HttpStatusCode.OK)

            val eventsAfterClear = SergMockJson.decodeFromString<Hendelser>(client.get("/hendelser").bodyAsText())
            assertThat(eventsAfterClear.hendelser).isEqualTo(emptyList())
        }

    @Test
    fun `admin page contains generation controls`() =
        testApplication {
            configureMockApp()

            val response = client.get("/admin")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("Generer")
            assertThat(response.bodyAsText()).contains("Antall hendelser")
        }

    private fun ApplicationTestBuilder.configureMockApp() {
        application {
            configureSergMockApp(
                config = SergMockConfig(httpPort = 8084, maxGenerateBatch = 10_000),
                store = MockDataStore(maxGenerateBatch = 10_000),
            )
        }
    }
}
