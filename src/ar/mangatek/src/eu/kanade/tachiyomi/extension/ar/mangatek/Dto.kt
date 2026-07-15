package eu.kanade.tachiyomi.extension.ar.mangatek

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WrappedSerializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<Wrapped<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Wrapped")

    override fun deserialize(decoder: Decoder): Wrapped<T> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected Json Decoder")
        val array = input.decodeJsonElement().jsonArray

        // array[0] هو المؤشر (index)، و array[1] هو المحتوى الفعلي
        val index = array[0].jsonPrimitive.int
        val value = input.json.decodeFromJsonElement(dataSerializer, array[1])

        return Wrapped(index, value)
    }

    override fun serialize(encoder: Encoder, value: Wrapped<T>) {
        throw SerializationException("Serialization is not supported")
    }
}

@Serializable(with = WrappedSerializer::class)
class Wrapped<T>(
    val index: Int,
    val value: T
)

@Serializable
class MangaWrapper(
    val manga: Wrapped<MangaData>
)

@Serializable
class MangaData(
    @SerialName("MangaChapters")
    val mangaChapters: Wrapped<List<Wrapped<ChapterItem>>>
)

@Serializable
class ChapterItem(
    @SerialName("chapter_number") val chapterNumber: Wrapped<String>,
    val title: Wrapped<String?>,
    @SerialName("created_at") val createdAt: Wrapped<String?>
)

@Serializable
class PageDTO(
    val imageUrl: String,
    val bubbles: List<Bubble> = emptyList()
) {
    fun hasSpeechBubbles(): Boolean = bubbles.isNotEmpty()
}

/**
 * بيانات الفقاعة النصية المحسّنة
 * مع دعم أنواع مختلفة من الفقاعات والذكاء الاصطناعي
 */
@Serializable
class Bubble(
    val text: String = "",
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val width: Float = 0.0f,
    val height: Float = 0.0f,
    val angle: Float = 0.0f,
    // ألوان مخصصة (اختيارية)
    val bgColor: String? = null, // لون الخلفية (hex: #RRGGBB أو #AARRGGBB)
    val textColor: String? = null, // لون النص (hex: #RRGGBB أو #AARRGGBB)
    // نوع الفقاعة
    val type: String = "normal", // normal, shout, whisper, thought
    // اتجاه النص
    val direction: String? = null // rtl أو ltr
) {
    /**
     * كشف نوع الفقاعة تلقائياً بناءً على خصائص النص
     */
    fun detectBubbleType(): String = when {
        text.isEmpty() -> "normal"
        text.count { it.isUpperCase() } > text.length * 0.6 -> "shout"
        (text.contains("!!!") || text.contains("!")) && text.length < 10 -> "shout"
        text.startsWith("(") && text.endsWith(")") -> "whisper"
        text.startsWith("...") -> "thought"
        else -> "normal"
    }

    /**
     * كشف اتجاه النص (RTL للعربية، LTR للإنجليزية)
     */
    fun detectDirection(): String = when {
        text.any { it.code in 0x0600..0x06FF } -> "rtl" // عربي
        text.any { it.code in 0x0590..0x05FF } -> "rtl" // عبري
        else -> "ltr"
    }

    fun getBackgroundColor(): Int = when {
        bgColor != null -> hexToColor(bgColor, 0xFFFFFFFF.toInt())
        type == "shout" -> 0xFFFFCC00.toInt()
        type == "whisper" -> 0xFFE0E0E0.toInt()
        type == "thought" -> 0xFFFFC0CB.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun getTextColor(): Int = when {
        textColor != null -> hexToColor(textColor, 0xFF000000.toInt())
        type == "shout" -> 0xFF000000.toInt()
        type == "whisper" -> 0xFF666666.toInt()
        else -> 0xFF000000.toInt()
    }

    private fun hexToColor(hex: String, default: Int): Int {
        return try {
            val cleanHex = hex.removePrefix("#")
            val color = cleanHex.toLongOrNull(16) ?: return default
            if (cleanHex.length == 8) {
                color.toInt() // يحتفظ بقيمة الشفافية (Alpha) إذا تم توفيرها
            } else {
                (color or 0xFF000000L).toInt() // يضيف شفافية كاملة تلقائياً
            }
        } catch (e: Exception) {
            default
        }
    }
}

@Serializable
data class TranslationStats(
    val totalBubbles: Int = 0,
    val processedBubbles: Int = 0,
    val failedBubbles: Int = 0,
    val averageProcessingTime: Long = 0,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0
) {
    val cacheHitRate: Float
        get() = if (cacheHits + cacheMisses > 0) cacheHits.toFloat() / (cacheHits + cacheMisses) else 0f

    val successRate: Float
        get() = if (totalBubbles > 0) processedBubbles.toFloat() / totalBubbles else 0f
}

@Serializable
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val message: String = "",
    val bubbleIndex: Int? = null,
    val exception: String? = null
)

object TranslationDictionary {
    private val corrections = mapOf(
        "ا الـ" to "ال",
        "الـ " to "ال ",
        "  " to " ",
        "؟؟" to "؟",
        "!!!" to "!",
        "،،" to "،",
        "ك ل" to "كل",
        "ف ي" to "في",
        "ع ن" to "عن"
    )

    private val arabicStopWords = setOf(
        "ال", "و", "أو", "من", "في", "ب", "ل", "ك", "عن", "على", "إلى", "هذا", "ذلك"
    )

    fun correct(text: String): String {
        var corrected = text
        for ((wrong, correct) in corrections) {
            corrected = corrected.replace(wrong, correct, ignoreCase = false)
        }
        return corrected.trim()
    }

    fun isStopWord(word: String): Boolean = arabicStopWords.contains(word)

    fun removeStopWords(text: String): String {
        return text.split(" ")
            .filterNot { isStopWord(it) }
            .joinToString(" ")
    }
}

/**
 * مدير التخزين المؤقت للترجمات (Thread-Safe LRU Cache)
 */
object TranslationCache {
    private const val MAX_CACHE_SIZE = 1000

    // إنشاء LRU Cache آمن للـ Threads
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    // استخدام @Volatile لضمان مزامنة البيانات بين المسارات المختلفة بشكل آمن
    @Volatile
    private var stats = TranslationStats()

    fun get(key: String): String? {
        val value = cache[key]
        synchronized(this) {
            stats = if (value != null) {
                stats.copy(cacheHits = stats.cacheHits + 1)
            } else {
                stats.copy(cacheMisses = stats.cacheMisses + 1)
            }
        }
        return value
    }

    fun put(key: String, value: String) {
        cache[key] = value
    }

    fun clear() {
        cache.clear()
        synchronized(this) {
            stats = TranslationStats()
        }
    }

    fun getStats(): TranslationStats = stats

    @Synchronized
    fun updateStats(processed: Int, failed: Int, time: Long) {
        stats = stats.copy(
            processedBubbles = stats.processedBubbles + processed,
            failedBubbles = stats.failedBubbles + failed,
            averageProcessingTime = (stats.averageProcessingTime + time) / 2
        )
    }
}

/**
 * نظام التسجيل (Thread-Safe Logging System)
 */
object LoggerService {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOG_SIZE = 500

    @Volatile
    private var enableLogging = true

    fun info(message: String, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(LogEntry(level = "INFO", message = message, bubbleIndex = bubbleIndex))
        }
    }

    fun warning(message: String, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(LogEntry(level = "WARNING", message = message, bubbleIndex = bubbleIndex))
        }
    }

    fun error(message: String, exception: Exception? = null, bubbleIndex: Int? = null) {
        if (enableLogging) {
            addLog(
                LogEntry(
                    level = "ERROR",
                    message = message,
                    exception = exception?.message,
                    bubbleIndex = bubbleIndex
                )
            )
        }
    }

    @Synchronized
    private fun addLog(entry: LogEntry) {
        logs.add(entry)
        if (logs.size > MAX_LOG_SIZE) {
            logs.removeAt(0)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }

    fun setLogging(enabled: Boolean) {
        enableLogging = enabled
    }
}

/**
 * نظام تحسين الأداء والقياسات (Thread-Safe)
 */
object PerformanceMonitor {
    private val timings = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()

    /**
     * دالة لقياس وقت التنفيذ بشكل آمن ومباشر
     */
    inline fun <T> measure(operationName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - start
            recordTiming(operationName, duration)
        }
    }

    fun recordTiming(operationName: String, duration: Long) {
        timings.getOrPut(operationName) { CopyOnWriteArrayList() }.add(duration)
    }

    fun getAverageTime(operationName: String): Long {
        val times = timings[operationName] ?: return 0
        return if (times.isNotEmpty()) times.average().toLong() else 0
    }

    fun getReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Performance Report ===")
        timings.forEach { (operation, times) ->
            if (times.isNotEmpty()) {
                sb.appendLine("$operation: avg=${times.average().toLong()}ms, total=${times.sum()}ms, count=${times.size}")
            }
        }
        return sb.toString()
    }

    fun clear() {
        timings.clear()
    }
}
