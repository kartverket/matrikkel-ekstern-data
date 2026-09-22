package no.kartverket.matrikkel.config

import no.kartverket.matrikkel.kafkaclient.Serde
import no.kartverket.tjenestespesifikasjoner.serg.hendelser.models.Hendelse
import org.openapitools.client.infrastructure.Serializer.jacksonObjectMapper

object HendelseSerde: Serde<Hendelse> {

    override fun serialize(data: Hendelse): ByteArray {
        return jacksonObjectMapper.writeValueAsBytes(data)
    }

    override fun deserialize(data: ByteArray): Hendelse {
        return jacksonObjectMapper.readValue(data,  Hendelse::class.java)
    }
}