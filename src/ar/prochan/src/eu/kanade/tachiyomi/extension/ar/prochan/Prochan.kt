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
    override val baseUrl = "https://procomic.pro/ar"
    override val lang = "ar"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept-Language", "ar-SA,ar;q=0.9,en;q=0.8")
        .add("Referer", baseUrl)
        .add("Accept", "application/json")

    // ==================== Popular Manga ====================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.body.string()
        val mangas = mutableListOf<SManga>()
        
        try {
            // Parse JSON response
            val json = Json.parseToJsonElement(document).jsonObject
            val seriesList = json["series"]?.jsonArray ?: return MangasPage(mangas, false)

            seriesList.forEach { series ->
                val manga = series.jsonObject
                mangas.add(
                    SManga.create().apply {
                        title = manga["title"]?.jsonPrimitive?.content ?: "Unknown"
                        url = "/invincible-after-shocking-my-empress-wife-${manga["id"]?.jsonPrimitive?.content ?: "0"}"
                        thumbnail_url = "https://app.procomic.pro/series-cards/${manga["id"]?.jsonPrimitive?.content ?: "0"}/cover.avif"
                        author = manga["author"]?.jsonPrimitive?.content ?: ""
                        description = manga["description"]?.jsonPrimitive?.content ?: ""
                        status = when (manga["status"]?.jsonPrimitive?.content) {
                            "ongoing" -> SManga.ONGOING
                            "completed" -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }
                        genre = manga["genres"]?.jsonArray?.mapNotNull {
                            it.jsonPrimitive.contentOrNull
                        }?.joinToString(", ") ?: ""
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hasNextPage = document.contains("\"next\"")
        return MangasPage(mangas, hasNextPage)
    }

    // ==================== Latest Updates ====================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/updates?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ==================== Search ====================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/series?search=$query&page=$page"
        } else {
            "$baseUrl/series?page=$page"
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
            val jsonData = Json.parseToJsonElement(document).jsonObject
            val manga = jsonData["manga"]?.jsonObject ?: return SManga.create()

            SManga.create().apply {
                title = manga["title"]?.jsonPrimitive?.content ?: ""
                author = manga["author"]?.jsonPrimitive?.content ?: ""
                artist = manga["artist"]?.jsonPrimitive?.content ?: ""
                description = manga["description"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = manga["coverUrl"]?.jsonPrimitive?.content ?: ""
                status = when (manga["status"]?.jsonPrimitive?.content) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                genre = manga["genres"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                }?.joinToString(", ") ?: ""
                initialized = true
            }
        } catch (e: Exception) {
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
            val jsonData = Json.parseToJsonElement(document).jsonObject
            val chaptersList = jsonData["chapters"]?.jsonArray ?: return chapters

            chaptersList.forEachIndexed { index, chapter ->
                val ch = chapter.jsonObject
                chapters.add(
                    SChapter.create().apply {
                        name = ch["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                        url = "/chapter/${ch["id"]?.jsonPrimitive?.content ?: ""}"
                        chapter_number = ch["number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                        date_upload = try {
                            val dateStr = ch["uploadDate"]?.jsonPrimitive?.content ?: ""
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(dateStr)?.time ?: 0
                        } catch (e: Exception) {
                            0L
                        }
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chapters.reversed()
    }

    // ==================== Pages ====================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.removePrefix("/chapter/")
        return GET("$baseUrl/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.body.string()
        val pages = mutableListOf<Page>()

        try {
            val jsonData = Json.parseToJsonElement(document).jsonObject
            val pagesList = jsonData["pages"]?.jsonArray ?: return pages

            pagesList.forEachIndexed { index, page ->
                val p = page.jsonObject
                val imageUrl = p["imageUrl"]?.jsonPrimitive?.content ?: ""
                if (imageUrl.isNotEmpty()) {
                    pages.add(Page(index, imageUrl, imageUrl))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    companion object {
        private const val TAG = "Prochan"
        const val BASE_URL = "https://procomic.pro/ar"
        const val UPDATES_URL = "https://procomic.pro/ar/updates"
        const val SERIES_URL = "https://procomic.pro/ar/series"
    }
}
