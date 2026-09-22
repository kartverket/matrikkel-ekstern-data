package no.kartverket.matrikkel.config

import no.kartverket.matrikkel.kafkaclient.Serde
import no.kartverket.tjenestespesifikasjoner.serg.formueobjekt.models.FastEiendomSomFormuesobjekt
import org.openapitools.client.infrastructure.Serializer.jacksonObjectMapper

object FastEiendomSomFormuesobjektSerde: Serde<FastEiendomSomFormuesobjekt> {

    override fun serialize(data: FastEiendomSomFormuesobjekt): ByteArray {
        return jacksonObjectMapper.writeValueAsBytes(data)
    }

    override fun deserialize(data: ByteArray): FastEiendomSomFormuesobjekt {
        return jacksonObjectMapper.readValue(data,  FastEiendomSomFormuesobjekt::class.java)
    }
}