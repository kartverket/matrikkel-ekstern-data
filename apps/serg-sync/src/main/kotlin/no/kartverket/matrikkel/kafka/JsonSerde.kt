package no.kartverket.matrikkel.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import no.kartverket.matrikkel.kafkaclient.Serde

inline fun <reified T> JsonSerde(): Serde<T> {

    val json = Json {
        ignoreUnknownKeys = true
    }

    return object : Serde<T> {
        override fun serialize(data: T): ByteArray {
            return json.encodeToString(serializer<T>(), data).encodeToByteArray()
        }

        override fun deserialize(data: ByteArray): T {
            return json.decodeFromString(serializer<T>(), data.decodeToString())
        }
    }
}