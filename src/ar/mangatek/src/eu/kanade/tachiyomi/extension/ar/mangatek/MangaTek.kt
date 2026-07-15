package eu.kanade.tachiyomi.extension.ar.mangatek

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.Charsets

@Source
abstract class MangaTek :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)?.toIntOrNull() ?: DEFAULT_FONT_SIZE.toInt()

    private val translationWaitTime: Int
        get() = preferences.getString(TRANSLATION_WAIT_PREF, DEFAULT_TRANSLATION_WAIT)?.toIntOrNull() ?: DEFAULT_TRANSLATION_WAIT.toInt()

    private val maxRetries: Int
        get() = preferences.getString(MAX_RETRIES_PREF, DEFAULT_MAX_RETRIES)?.toIntOrNull() ?: DEFAULT_MAX_RETRIES.toInt()

    private val retryDelay: Int
        get() = preferences.getString(RETRY_DELAY_PREF, DEFAULT_RETRY_DELAY)?.toIntOrNull() ?: DEFAULT_RETRY_DELAY.toInt()

    override val client: OkHttpClient by lazy {
        val baseClient = network.client
        baseClient.newBuilder()
            .addInterceptor(SpeechBubblePainterInterceptor({ fontSize }, httpClient = baseClient))
            .rateLimit(3)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: "Unknown"
            description = document.selectFirst("p.text-base")?.text()
            genre = document
                .selectFirst("p > span:contains(التصنيفات:) + span")
                ?.text()?.replace("،", ",")
            status = document.selectFirst(".flex span.border.rounded")?.text().toStatus()
            thumbnail_url = document.selectFirst("img#mangaCover")?.imgAttr()
            author = document
                .selectFirst("p > span:contains(المؤلف:) + span")
                ?.ownText()
                ?.takeIf { it != "Unknown" }
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")
        val props = response.asJsoup()
            .selectFirst("astro-island[component-url*=MangaChaptersLoader]")
            ?.attr("props") ?: return emptyList()

        val data = props.parseAs<MangaWrapper>()
        val chapters = data.manga.value.mangaChapters.value.map { it.value }

        return chapters.map { ch ->
            SChapter.create().apply {
                name = ch.title.value?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.chapterNumber.value.toFormattedString()}"
                url = "/reader/$seriesSlug/${ch.chapterNumber.value.toFormattedString()}"
                date_upload = dateFormat.tryParse(ch.createdAt.value)
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        var document = response.asJsoup()

        try {
            Thread.sleep(translationWaitTime.toLong().coerceAtMost(10000L))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        var pageDTOs = getPages(document)
        var retries = 0

        while (pageDTOs.isNotEmpty() && pageDTOs.any { !it.hasSpeechBubbles() } && retries < maxRetries) {
            try {
                Thread.sleep(retryDelay.toLong())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            try {
                val request = response.request
                client.newCall(request).execute().use { newResponse ->
                    if (newResponse.isSuccessful) {
                        document = newResponse.asJsoup()
                        val newPageDTOs = getPages(document)

                        if (newPageDTOs.isNotEmpty() && newPageDTOs.any { it.hasSpeechBubbles() }) {
                            pageDTOs = newPageDTOs
                            break
                        }
                    }
                }
            } catch (e: Exception) {
            }
            retries++
        }

        return pageDTOs.mapIndexed { index, pageDto ->
            val imageUrl = if (pageDto.hasSpeechBubbles()) {
                "${pageDto.imageUrl}${pageDto.bubbles.toJsonString().toFragment()}"
            } else {
                pageDto.imageUrl
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun getPages(document: Document): List<PageDTO> {
        return document.select(".manga-page").mapNotNull { element ->
            try {
                val imageUrl = element.selectFirst("img")?.imgAttr() ?: return@mapNotNull null
                val overlays = element.select(".text-overlay")
                val bubbles = overlays.mapNotNull { overlay ->
                    try {
                        val style = overlay.attr("style")
                        val text = overlay.text().trim()

                        if (text.isEmpty()) return@mapNotNull null

                        val leftValue = CSS_LEFT_REGEX.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: return@mapNotNull null
                        val topValue = CSS_TOP_REGEX.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: return@mapNotNull null
                        val widthValue = CSS_WIDTH_REGEX.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                        val heightValue = CSS_HEIGHT_REGEX.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                        val angleValue = CSS_ANGLE_REGEX.find(style)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

                        Bubble(
                            text = text,
                            left = leftValue,
                            top = topValue,
                            width = widthValue,
                            height = heightValue,
                            angle = angleValue,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                PageDTO(imageUrl, bubbles)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Performs full, standard-safe URI encoding on serialized JSON
    fun String.toFragment(): String = try {
        "#${URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")}"
    } catch (e: Exception) {
        "#${this.replace("#", "%23")}"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    private fun Double.toFormattedString(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context
        val fontSizePref = ListPreference(context).apply {
            key = FONT_SIZE_PREF
            title = "حجم خط الترجمة"
            summary = "%s"
            entries = arrayOf("20", "24", "28", "32", "36", "40")
            entryValues = arrayOf("20", "24", "28", "32", "36", "40")
            setDefaultValue(DEFAULT_FONT_SIZE)
        }

        val translationWaitPref = ListPreference(context).apply {
            key = TRANSLATION_WAIT_PREF
            title = "وقت انتظار معالجة الترجمة"
            summary = "%s"
            entries = arrayOf("3 ثوانٍ", "5 ثوانٍ", "10 ثوانٍ (افتراضي)", "20 ثانية")
            entryValues = arrayOf("3000", "5000", "10000", "20000")
            setDefaultValue(DEFAULT_TRANSLATION_WAIT)
        }

        val maxRetriesPref = ListPreference(context).apply {
            key = MAX_RETRIES_PREF
            title = "أقصى عدد لمحاولات تحديث الصفحة"
            summary = "%s محاولات"
            entries = arrayOf("3 محاولات", "5 محاولات (افتراضي)", "8 محاولات", "10 محاولات")
            entryValues = arrayOf("3", "5", "8", "10")
            setDefaultValue(DEFAULT_MAX_RETRIES)
        }

        val retryDelayPref = ListPreference(context).apply {
            key = RETRY_DELAY_PREF
            title = "المدة الزمنية بين المحاولات"
            summary = "%s"
            entries = arrayOf("3 ثوانٍ", "5 ثوانٍ (افتراضي)", "7 ثوانٍ", "10 ثوانٍ")
            entryValues = arrayOf("3000", "5000", "7000", "10000")
            setDefaultValue(DEFAULT_RETRY_DELAY)
        }

        screen.addPreference(fontSizePref)
        screen.addPreference(translationWaitPref)
        screen.addPreference(maxRetriesPref)
        screen.addPreference(retryDelayPref)
}
class MangaTek : HttpSource() { // Ensure this matches your actual class name

    // ... existing class code ...

    companion object {
        // ktlint-disable
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?[^#]*)?#.*""", RegexOption.IGNORE_CASE)
        // ktlint-enable

        private val CSS_LEFT_REGEX = Regex("""\bleft:\s*([\d.]+)\s*%""")
        private val CSS_TOP_REGEX = Regex("""\btop:\s*([\d.]+)\s*%""")
        private val CSS_WIDTH_REGEX = Regex("""\bwidth:\s*([\d.]+)\s*%""")
        private val CSS_HEIGHT_REGEX = Regex("""\bheight:\s*([\d.]+)\s*%""")
        private val CSS_ANGLE_REGEX = Regex("""\bangle:\s*([\d.-]+)\s*deg""")

        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val DEFAULT_FONT_SIZE = "28"

        private const val TRANSLATION_WAIT_PREF = "translationWaitPref"
        private const val DEFAULT_TRANSLATION_WAIT = "10000"

        private const val MAX_RETRIES_PREF = "maxRetriesPref"
        private const val DEFAULT_MAX_RETRIES = "5"

        private const val RETRY_DELAY_PREF = "retryDelayPref"
        private const val DEFAULT_RETRY_DELAY = "5000"
    }
}
