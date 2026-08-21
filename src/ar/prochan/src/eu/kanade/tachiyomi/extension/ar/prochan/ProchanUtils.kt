package eu.kanade.tachiyomi.extension.ar.prochan

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProchanUtils {

    // ==================== Date Parsing ====================
    fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L

        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy",
                "yyyy-MM-dd"
            )

            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    return sdf.parse(dateStr)?.time ?: 0L
                } catch (e: Exception) {
                    continue
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat(ProchanConstants.DATE_FORMAT_DISPLAY, Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    fun formatDateArabic(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat(ProchanConstants.DATE_FORMAT_DISPLAY_AR, Locale("ar", "SA"))
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== URL Utilities ====================
    fun getSlugFromUrl(url: String): String {
        return url.removePrefix("/").split("/").lastOrNull() ?: ""
    }

    fun getMangaIdFromUrl(url: String): String {
        val slug = getSlugFromUrl(url)
        return slug.split("-").lastOrNull() ?: ""
    }

    fun getChapterIdFromUrl(url: String): String {
        return url.removePrefix("/chapter/").split("/").firstOrNull() ?: ""
    }

    fun buildMangaUrl(slug: String): String {
        return "/${slug.removePrefix("/")}"
    }

    fun buildChapterUrl(chapterId: Int): String {
        return "/chapter/$chapterId"
    }

    fun buildImageUrl(seriesId: Int, imageName: String): String {
        return "${ProchanConstants.SERIES_CARDS_URL}/$seriesId/$imageName"
    }

    // ==================== String Utilities ====================
    fun sanitizeTitle(title: String?): String {
        return title?.trim()?.replace(Regex("\\s+"), " ") ?: ""
    }

    fun sanitizeDescription(description: String?): String {
        return description?.trim()?.replace(Regex("<[^>]*>"), "") ?: ""
    }

    fun truncateText(text: String, maxLength: Int = 200): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }

    fun normalizeGenre(genre: String): String {
        return genre.trim().lowercase()
    }

    // ==================== Number Utilities ====================
    fun formatViewCount(views: Long?): String {
        if (views == null || views == 0L) return "0"

        return when {
            views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format("%.1fK", views / 1_000.0)
            else -> views.toString()
        }
    }

    fun formatRating(rating: Float?): String {
        return if (rating != null) {
            String.format("%.1f", rating)
        } else {
            "N/A"
        }
    }

    fun chapterNumberToFloat(chapterStr: String?): Float {
        if (chapterStr.isNullOrEmpty()) return 0f

        return try {
            chapterStr.toFloatOrNull() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    // ==================== Status Translation ====================
    fun translateStatus(status: String?): Int {
        return when (status?.lowercase()) {
            ProchanConstants.STATUS_ONGOING -> 1 // SManga.ONGOING
            ProchanConstants.STATUS_COMPLETED -> 2 // SManga.COMPLETED
            ProchanConstants.STATUS_HIATUS -> 3 // SManga.ON_HIATUS
            else -> 0 // SManga.UNKNOWN
        }
    }

    fun getStatusText(status: String?): String {
        return when (status?.lowercase()) {
            ProchanConstants.STATUS_ONGOING -> "مستمر"
            ProchanConstants.STATUS_COMPLETED -> "مكتمل"
            ProchanConstants.STATUS_HIATUS -> "متوقف مؤقتاً"
            ProchanConstants.STATUS_DROPPED -> "مرفوض"
            else -> "غير معروف"
        }
    }

    // ==================== List Utilities ====================
    fun parseGenresList(genresJson: List<String>?): String {
        return genresJson?.joinToString(", ") ?: ""
    }

    fun parseGenresArray(genresList: String): List<String> {
        return genresList.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun filterGenresByCategory(genres: List<String>, category: String): List<String> {
        return genres.filter { it.contains(category, ignoreCase = true) }
    }

    // ==================== Validation ====================
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun isValidImageUrl(url: String?): Boolean {
        if (!isValidUrl(url)) return false
        val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "avif", "gif")
        return imageExtensions.any { url?.endsWith(it, ignoreCase = true) ?: false }
    }

    fun isValidMangaId(id: String?): Boolean {
        return !id.isNullOrEmpty() && id.toIntOrNull() != null
    }

    fun isValidChapterId(id: String?): Boolean {
        return !id.isNullOrEmpty() && id.toIntOrNull() != null
    }

    // ==================== Encoding/Decoding ====================
    fun encodeUrl(url: String): String {
        return java.net.URLEncoder.encode(url, "UTF-8")
    }

    fun decodeUrl(url: String): String {
        return java.net.URLDecoder.decode(url, "UTF-8")
    }

    // ==================== Error Handling ====================
    fun getErrorMessage(errorCode: Int?): String {
        return when (errorCode) {
            400 -> ProchanConstants.ErrorMessages.LOADING_FAILED
            401 -> ProchanConstants.ErrorMessages.UNAUTHORIZED
            403 -> ProchanConstants.ErrorMessages.FORBIDDEN
            404 -> ProchanConstants.ErrorMessages.NOT_FOUND
            500 -> ProchanConstants.ErrorMessages.SERVER_ERROR
            else -> ProchanConstants.ErrorMessages.LOADING_FAILED
        }
    }

    // ==================== Text Analysis ====================
    fun calculateReadingTime(pageCount: Int): Int {
        // Assuming average 2 minutes per page
        return pageCount * 2
    }

    fun getChapterProgress(currentChapter: Float, totalChapters: Int): Float {
        return if (totalChapters > 0) {
            (currentChapter / totalChapters) * 100
        } else {
            0f
        }
    }

    // ==================== String Comparison ====================
    fun isSimilarTitle(title1: String, title2: String): Boolean {
        val normalized1 = title1.lowercase().trim()
        val normalized2 = title2.lowercase().trim()
        return normalized1 == normalized2 || 
               normalized1.replace(Regex("[^a-zA-Z0-9]"), "") == 
               normalized2.replace(Regex("[^a-zA-Z0-9]"), "")
    }

    // ==================== Language Utilities ====================
    fun isArabicText(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' }
    }

    fun containsArabic(text: String): Boolean {
        return isArabicText(text)
    }
}
