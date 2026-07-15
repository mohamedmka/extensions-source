package eu.kanade.tachiyomi.extension.ar.mangatek

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class WrappedSerializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<Wrapped<T>> {
    override val descriptor: SerialDescriptor =
        dataSerializer.descriptor

    override fun deserialize(decoder: Decoder): Wrapped<T> {
        val json = (decoder as kotlinx.serialization.json.JsonDecoder).decodeJsonElement().jsonArray
        val index = json[0].jsonPrimitive.int
        val value = kotlinx.serialization.json.Json.decodeFromJsonElement(dataSerializer, json[1])
        return Wrapped(index, value)
    }

    override fun serialize(encoder: Encoder, value: Wrapped<T>): Unit =
        throw SerializationException("Serialization is not supported")
}

@Serializable(with = WrappedSerializer::class)
class Wrapped<T>(val index: Int, val value: T)

@Serializable
class MangaWrapper(
    val manga: Wrapped<MangaData>,
)

@Serializable
class MangaData(
    val title: Wrapped<String?>,
    val mangaChapters: Wrapped<List<Wrapped<ChapterData>>>,
)

@Serializable
class ChapterData(
    val title: Wrapped<String?>,
    val chapterNumber: Wrapped<Double>,
    val createdAt: Wrapped<String>,
)
