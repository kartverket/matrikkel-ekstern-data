package no.kartverket.matrikkel.config

import no.kartverket.matrikkel.kafkaclient.Serde
import org.openapitools.client.infrastructure.Serializer.jacksonObjectMapper

inline fun <reified T> JsonSerde(): Serde<T> {
    return object : Serde<T> {
        override fun serialize(data: T): ByteArray {
            return jacksonObjectMapper.writeValueAsBytes(data)
        }

        override fun deserialize(data: ByteArray): T {
            return jacksonObjectMapper.readValue<T>(data, T::class.java)
        }
    }
}