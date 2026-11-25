package no.kartverket.matrikkel

import no.kartverket.eksterndata.domene.Brreg
import no.kartverket.tjenestespesifikasjoner.brreg.apis.EnheterApi
import no.kartverket.tjenestespesifikasjoner.brreg.apis.OppdateringerApi
import no.kartverket.tjenestespesifikasjoner.brreg.apis.UnderenheterApi
import no.kartverket.tjenestespesifikasjoner.brreg.models.Enhet
import no.kartverket.tjenestespesifikasjoner.brreg.models.SlettetEnhet
import no.kartverket.tjenestespesifikasjoner.brreg.models.SlettetUnderEnhet
import no.kartverket.tjenestespesifikasjoner.brreg.models.Underenhet
import kotlin.time.Clock

const val ENHET_OFFSET = "enhet_offset"
const val UNDERENHET_OFFSET = "underenhet_offset"

class BrregService(
    val enheterApi: EnheterApi,
    val underenheterApi: UnderenheterApi,
    val oppdateringerApi: OppdateringerApi,
) {
    fun hentStartEnhetSekvensnummerForDato(dato: String): Long {
        val response = oppdateringerApi.hentOppdateringerForEnheter(dato = dato, size = "1")
        val oppdateringId = response.embedded?.oppdaterteEnheter?.get(0)?.oppdateringsid?.toLong()
        return requireNotNull(oppdateringId) {
            "Kunne ikke hente oppdateringId for enheter for dato $dato"
        }
    }

    fun hentStartUnderenhetSekvensnummerForDato(dato: String): Long {
        val response = oppdateringerApi.hentOppdateringerForUnderenheter(dato = dato, size = "1")
        val oppdateringId = response.embedded?.oppdaterteUnderenheter?.get(0)?.oppdateringsid?.toLong()
        return requireNotNull(oppdateringId) {
            "Kunne ikke hente oppdateringId for underenheter for dato $dato"
        }
    }

    fun hentOppdateringerForEnheter(sekvensnummer: Long): List<Brreg.Hendelse> {
        val response = oppdateringerApi.hentOppdateringerForEnheter(oppdateringsid = "$sekvensnummer", size = "100")
        val oppdateringer = response.embedded?.oppdaterteEnheter ?: emptyList()
        return oppdateringer.map { it.toKafkaDTO() }
    }

    fun hentOppdateringerForUnderenheter(sekvensnummer: Long): List<Brreg.Hendelse> {
        val response = oppdateringerApi.hentOppdateringerForUnderenheter(oppdateringsid = "$sekvensnummer", size = "100")
        val oppdateringer = response.embedded?.oppdaterteUnderenheter ?: emptyList()
        return oppdateringer.map { it.toKafkaDTO() }
    }

    fun hentVirksomhet(
        hendelse: Brreg.Hendelse,
        clock: Clock = Clock.System,
    ): Brreg.Virksomhet {
        return when (hendelse.registerType) {
            Brreg.RegisterType.ENHET -> hentEnhet(hendelse.organisasjonsnr, clock)
            Brreg.RegisterType.UNDERENHET -> hentUnderenhet(hendelse.organisasjonsnr, clock)
        }
    }

    private fun hentEnhet(
        organisasjonsnr: String,
        clock: Clock = Clock.System,
    ): Brreg.Virksomhet {
        val response = enheterApi.hentEnhet(organisasjonsnr)
        return when (response) {
            is Enhet -> response.toKafkaDTO()
            is SlettetEnhet -> response.toKafkaDTO(clock.currentDate())
            else -> error("ukjent respons type ${response.responsKlasse}")
        }
    }

    private fun hentUnderenhet(
        organisasjonsnr: String,
        clock: Clock = Clock.System,
    ): Brreg.Virksomhet {
        val response = underenheterApi.hentUnderenhet(organisasjonsnr)
        return when (response) {
            is Underenhet -> response.toKafkaDTO()
            is SlettetUnderEnhet -> response.toKafkaDTO(clock.currentDate())
            else -> error("ukjent respons type ${response.responsKlasse}")
        }
    }
}