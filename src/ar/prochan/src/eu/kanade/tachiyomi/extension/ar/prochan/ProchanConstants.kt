package eu.kanade.tachiyomi.extension.ar.prochan

object ProchanConstants {
    // Base URLs
    const val BASE_URL = "https://procomic.pro"
    const val BASE_URL_AR = "https://procomic.pro/ar"
    const val API_BASE_URL = "https://api.procomic.pro"
    const val CDN_URL = "https://app.procomic.pro"

    // Endpoints
    const val SERIES_ENDPOINT = "/series"
    const val UPDATES_ENDPOINT = "/updates"
    const val SEARCH_ENDPOINT = "/search"
    const val CHAPTER_ENDPOINT = "/chapter"
    const val POPULAR_ENDPOINT = "/popular"
    const val TRENDING_ENDPOINT = "/trending"

    // Image URLs
    const val SERIES_CARDS_URL = "$CDN_URL/series-cards"
    const val CHAPTER_IMAGES_URL = "$CDN_URL/chapters"
    const val PROFILE_IMAGES_URL = "$CDN_URL/profiles"

    // Image Extensions
    const val IMAGE_EXTENSION_AVIF = "avif"
    const val IMAGE_EXTENSION_WEBP = "webp"
    const val IMAGE_EXTENSION_JPG = "jpg"
    const val IMAGE_EXTENSION_PNG = "png"

    // Default Values
    const val DEFAULT_PAGE_SIZE = 20
    const val DEFAULT_TIMEOUT = 30000L
    const val DEFAULT_CONNECT_TIMEOUT = 15000L
    const val MAX_RETRIES = 3

    // Headers
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val ACCEPT_LANGUAGE = "ar-SA,ar;q=0.9,en;q=0.8"
    const val ACCEPT_ENCODING = "gzip, deflate, br"
    const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"

    // Status Constants
    const val STATUS_ONGOING = "ongoing"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_HIATUS = "hiatus"
    const val STATUS_DROPPED = "dropped"

    // Genres - Arabic
    object Genres {
        const val ACTION = "أكشن"
        const val ADVENTURE = "مغامرة"
        const val COMEDY = "كوميديا"
        const val DRAMA = "درامي"
        const val FANTASY = "خيال"
        const val HORROR = "رعب"
        const val ISEKAI = "ايسيكاي"
        const val MYSTERY = "غموض"
        const val ROMANCE = "رومانسي"
        const val SCHOOL = "مدرسي"
        const val SCI_FI = "خيال علمي"
        const val SLICE_OF_LIFE = "حياة يومية"
        const val SUPERNATURAL = "خارق للطبيعة"
        const val PSYCHOLOGICAL = "نفسي"
        const val SPORTS = "رياضي"
        const val SHOUJO = "شوجو"
        const val SHOUNEN = "شونين"
        const val SEINEN = "سيينين"
        const val JOSEI = "جوسي"
        const val BL = "BL"
        const val YURI = "Yuri"
    }

    // Categories
    object Categories {
        const val MANHWA = "مانهوا"
        const val MANGA = "مانجا"
        const val WEBTOON = "Webtoon"
        const val LIGHT_NOVEL = "Light Novel"
    }

    // Sort Options
    object SortOptions {
        const val LATEST = "latest"
        const val POPULAR = "popular"
        const val TRENDING = "trending"
        const val RATING = "rating"
        const val VIEWS = "views"
        const val ALPHABETICAL = "alphabetical"
    }

    // Cache & Storage
    const val CACHE_SIZE = 50 * 1024 * 1024L // 50 MB
    const val CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 hours

    // Preferences Keys
    const val PREF_KEY_QUALITY = "image_quality"
    const val PREF_KEY_SAVE_HISTORY = "save_history"
    const val PREF_KEY_LANGUAGE = "language"
    const val PREF_KEY_THEME = "theme"

    // Error Messages
    object ErrorMessages {
        const val NO_INTERNET = "لا يوجد اتصال إنترنت"
        const val LOADING_FAILED = "فشل تحميل البيانات"
        const val TIMEOUT = "انتهت مهلة الاتصال"
        const val SERVER_ERROR = "خطأ في الخادم"
        const val NOT_FOUND = "لم يتم العثور على المحتوى"
        const val UNAUTHORIZED = "غير مصرح"
        const val FORBIDDEN = "ممنوع"
    }

    // Date Formats
    const val DATE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    const val DATE_FORMAT_DISPLAY = "dd/MM/yyyy"
    const val DATE_FORMAT_DISPLAY_AR = "dd MMMM yyyy"
}
