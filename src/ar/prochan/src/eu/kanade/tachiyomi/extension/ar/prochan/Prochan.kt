package eu.kanade.tachiyomi.extension.ar.prochan

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class Prochan : HttpSource(), ConfigurableSource {

    override val name = "Prochan"
    override val baseUrl = ProchanConstants.BASE_URL_AR
    override val lang = "ar"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", ProchanConstants.USER_AGENT)
        .add("Accept-Language", ProchanConstants.ACCEPT_LANGUAGE)
        .add("Referer", baseUrl)
        .add("Accept", "application/json")

    // ==================== Popular Manga ====================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl${ProchanConstants.SERIES_ENDPOINT}?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.body.string()
        val mangas = mutableListOf<SManga>()
        
        try {
            if (!document.startsWith("{")) {
                return MangasPage(mangas, false)
            }

            val json = Json.parseToJsonElement(document).jsonObject
            val seriesList = json["series"]?.jsonArray ?: json["data"]?.jsonArray ?: return MangasPage(mangas, false)

            seriesList.forEach { series ->
                val manga = series.jsonObject
                val mangaId = manga["id"]?.jsonPrimitive?.content ?: "0"
                val slug = manga["slug"]?.jsonPrimitive?.content ?: "unknown"
                
                mangas.add(
                    SManga.create().apply {
                        title = manga["title"]?.jsonPrimitive?.content ?: "Unknown"
                        url = "/$slug-$mangaId"
                        thumbnail_url = manga["coverUrl"]?.jsonPrimitive?.content 
                            ?: "${ProchanConstants.SERIES_CARDS_URL}/$mangaId/cover.avif"
                        author = manga["author"]?.jsonPrimitive?.content ?: ""
                        description = manga["description"]?.jsonPrimitive?.content ?: ""
                        status = when (manga["status"]?.jsonPrimitive?.content?.lowercase()) {
                            ProchanConstants.STATUS_ONGOING -> SManga.ONGOING
                            ProchanConstants.STATUS_COMPLETED -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }
                        genre = manga["genres"]?.jsonArray?.mapNotNull {
                            it.jsonPrimitive.contentOrNull
                        }?.joinToString(", ") ?: ""
                    }
                )
            }
        } catch (e: Exception) {
            ProchanLogger.e("Error parsing popular manga", e)
        }

        val hasNextPage = document.contains("\"hasNextPage\"") || document.contains("\"next\"")
        return MangasPage(mangas, hasNextPage)
    }

    // ==================== Latest Updates ====================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl${ProchanConstants.UPDATES_ENDPOINT}?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ==================== Search ====================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl${ProchanConstants.SERIES_ENDPOINT}?search=$query&page=$page"
        } else {
            "$baseUrl${ProchanConstants.SERIES_ENDPOINT}?page=$page"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ==================== Manga Details ====================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaUrl = manga.url.removePrefix("/")
        return GET("$baseUrl/$mangaUrl", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.body.string()
        
        return try {
            if (!document.startsWith("{")) {
                return SManga.create()
            }

            val jsonData = Json.parseToJsonElement(document).jsonObject
            val manga = jsonData["manga"]?.jsonObject ?: jsonData["data"]?.jsonObject ?: return SManga.create()

            SManga.create().apply {
                title = manga["title"]?.jsonPrimitive?.content ?: ""
                author = manga["author"]?.jsonPrimitive?.content ?: ""
                artist = manga["artist"]?.jsonPrimitive?.content ?: ""
                description = manga["description"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = manga["coverUrl"]?.jsonPrimitive?.content ?: ""
                status = when (manga["status"]?.jsonPrimitive?.content?.lowercase()) {
                    ProchanConstants.STATUS_ONGOING -> SManga.ONGOING
                    ProchanConstants.STATUS_COMPLETED -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                genre = manga["genres"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                }?.joinToString(", ") ?: ""
                initialized = true
            }
        } catch (e: Exception) {
            ProchanLogger.e("Error parsing manga details", e)
            SManga.create()
        }
    }

    // ==================== Chapters ====================
    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = manga.url.removePrefix("/")
        return GET("$baseUrl/$mangaUrl", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.body.string()
        val chapters = mutableListOf<SChapter>()

        try {
            if (!document.startsWith("{")) {
                return chapters
            }

            val jsonData = Json.parseToJsonElement(document).jsonObject
            val chaptersList = jsonData["chapters"]?.jsonArray ?: return chapters

            chaptersList.forEachIndexed { index, chapter ->
                val ch = chapter.jsonObject
                chapters.add(
                    SChapter.create().apply {
                        name = ch["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                        url = "${ProchanConstants.CHAPTER_ENDPOINT}/${ch["id"]?.jsonPrimitive?.content ?: ""}"
                        chapter_number = ch["number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                        date_upload = try {
                            val dateStr = ch["uploadDate"]?.jsonPrimitive?.content ?: ""
                            ProchanUtils.parseDate(dateStr)
                        } catch (e: Exception) {
                            0L
                        }
                    }
                )
            }
        } catch (e: Exception) {
            ProchanLogger.e("Error parsing chapters", e)
        }

        return chapters.reversed()
    }

    // ==================== Pages ====================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.removePrefix(ProchanConstants.CHAPTER_ENDPOINT + "/")
        return GET("$baseUrl${ProchanConstants.CHAPTER_ENDPOINT}/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.body.string()
        val pages = mutableListOf<Page>()

        try {
            if (!document.startsWith("{")) {
                return pages
            }

            val jsonData = Json.parseToJsonElement(document).jsonObject
            val pagesList = jsonData["pages"]?.jsonArray ?: jsonData["data"]?.jsonArray ?: return pages

            pagesList.forEachIndexed { index, page ->
                val p = page.jsonObject
                val imageUrl = p["imageUrl"]?.jsonPrimitive?.content ?: ""
                if (imageUrl.isNotEmpty() && ProchanUtils.isValidImageUrl(imageUrl)) {
                    pages.add(Page(index, imageUrl, imageUrl))
                }
            }
        } catch (e: Exception) {
            ProchanLogger.e("Error parsing pages", e)
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    // ==================== Filters ====================
    override fun getFilterList(): FilterList {
        return ProchanFilters.getFilterList()
    }

    // ==================== Preferences ====================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // يمكن إضافة إعدادات المستخدم هنا
    }
}
