package eu.kanade.tachiyomi.extension.ar.prochan

import java.text.SimpleDateFormat
import java.util.Locale

object ProchanUtils {
    fun parseDate(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getSlugFromUrl(url: String): String {
        return url.removePrefix("/manga/")
    }

    fun getChapterIdFromUrl(url: String): String {
        return url.removePrefix("/chapter/")
    }
}
