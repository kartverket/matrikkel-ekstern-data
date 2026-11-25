package no.kartverket.matrikkel

import com.fasterxml.jackson.core.JsonToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.openapitools.client.infrastructure.Serializer
import java.io.InputStream
import java.util.zip.GZIPInputStream

fun InputStream.unzip() = GZIPInputStream(this)

inline fun <reified T> InputStream.parseJson(): Flow<T> = flow {
    val stream = this@parseJson
    val mapper = Serializer.jacksonObjectMapper
    stream.use { input ->
        mapper.createParser(input).use { parser ->
            check(parser.nextToken() == JsonToken.START_ARRAY) { "Expected an array, found ${parser.currentToken()}" }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                check(parser.currentToken() == JsonToken.START_OBJECT) { "Expected an object, found ${parser.currentToken()}" }
                emit(mapper.readValue(parser, T::class.java))
            }
        }
    }
}