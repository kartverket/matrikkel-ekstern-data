package no.kartverket.matrikkel.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.kartverket.matrikkel.kafkaclient.Serde

inline fun <reified T> JsonSerde(): Serde<T> {

    val kafkaJsonMapper: ObjectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    return object : Serde<T> {
        override fun serialize(data: T): ByteArray {
            return kafkaJsonMapper.writeValueAsBytes(data)
        }

        override fun deserialize(data: ByteArray): T {
            return kafkaJsonMapper.readValue<T>(data, T::class.java)
        }
    }
}