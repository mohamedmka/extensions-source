package eu.kanade.tachiyomi.extension.ar.prochan

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    override val baseUrl = "https://api.prochan.local"
    override val lang = "ar"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Android)")
        .add("Accept-Language", "ar-SA,ar;q=0.9")
        .add("Referer", "https://prochan.local/")

    // ==================== Popular ====================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/v1/manga/popular?page=$page&limit=20", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.body.string()
        val json = Json.parseToJsonElement(result).jsonObject

        val mangas = json["data"]?.jsonArray?.map { manga ->
            SManga.create().apply {
                val m = manga.jsonObject
                title = m["title"]?.jsonPrimitive?.content ?: "Unknown"
                thumbnail_url = m["coverUrl"]?.jsonPrimitive?.content ?: ""
                url = "/manga/${m["slug"]?.jsonPrimitive?.content ?: ""}"
                author = m["author"]?.jsonPrimitive?.content ?: ""
                description = m["description"]?.jsonPrimitive?.content ?: ""
                status = when (m["status"]?.jsonPrimitive?.content) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        } ?: emptyList()

        val hasNextPage = json["hasNextPage"]?.jsonPrimitive?.content?.toBoolean() ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ==================== Latest ====================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/v1/manga/latest?page=$page&limit=20", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ==================== Search ====================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/v1/manga/search?q=$query&page=$page&limit=20", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ==================== Manga Details ====================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/")
        return GET("$baseUrl/api/v1/manga/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.body.string()
        val json = Json.parseToJsonElement(result).jsonObject
        val manga = json["data"]?.jsonObject ?: return SManga.create()

        return SManga.create().apply {
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
        }
    }

    // ==================== Chapters ====================
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/")
        return GET("$baseUrl/api/v1/manga/$slug/chapters?limit=100", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.body.string()
        val json = Json.parseToJsonElement(result).jsonObject

        return json["data"]?.jsonArray?.mapIndexed { index, chapter ->
            val c = chapter.jsonObject
            SChapter.create().apply {
                name = c["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                url = "/chapter/${c["id"]?.jsonPrimitive?.content ?: ""}"
                chapter_number = c["number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                date_upload = try {
                    val dateStr = c["uploadDate"]?.jsonPrimitive?.content ?: ""
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(dateStr)?.time ?: 0
                } catch (e: Exception) {
                    0L
                }
            }
        }?.reversed() ?: emptyList()
    }

    // ==================== Pages ====================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.removePrefix("/chapter/")
        return GET("$baseUrl/api/v1/chapter/$chapterId/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.body.string()
        val json = Json.parseToJsonElement(result).jsonObject

        return json["data"]?.jsonArray?.mapIndexed { index, page ->
            val p = page.jsonObject
            Page(
                index = index,
                imageUrl = p["imageUrl"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    // ==================== Preferences ====================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // يمكن إضافة إعدادات هنا
    }

    companion object {
        private const val TAG = "Prochan"
    }
}
