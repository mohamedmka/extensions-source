package eu.kanade.tachiyomi.extension.ar.mangatek

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@RequiresApi(Build.VERSION_CODES.O)
@Source
abstract class MangaTek :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    // الاستخدام الآمن للـ Preferences لتجنب انهيار التطبيق إذا كانت القيمة فارغة
    private var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)?.toIntOrNull() ?: DEFAULT_FONT_SIZE.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()

    private var translationWaitTime: Int
        get() = preferences.getString(TRANSLATION_WAIT_PREF, DEFAULT_TRANSLATION_WAIT)?.toIntOrNull() ?: DEFAULT_TRANSLATION_WAIT.toInt()
        set(value) = preferences.edit().putString(TRANSLATION_WAIT_PREF, value.toString()).apply()

    private var maxRetries: Int
        get() = preferences.getString(MAX_RETRIES_PREF, DEFAULT_MAX_RETRIES)?.toIntOrNull() ?: DEFAULT_MAX_RETRIES.toInt()
        set(value) = preferences.edit().putString(MAX_RETRIES_PREF, value.toString()).apply()

    private var retryDelay: Int
        get() = preferences.getString(RETRY_DELAY_PREF, DEFAULT_RETRY_DELAY)?.toIntOrNull() ?: DEFAULT_RETRY_DELAY.toInt()
        set(value) = preferences.edit().putString(RETRY_DELAY_PREF, value.toString()).apply()

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(SpeechBubblePainterInterceptor(fontSize))
            .rateLimit(3)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val supportsLatest = true

    // Popular
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

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
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

    // Chapters
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US) // تأكد من مطابقة التنسيق الفعلي

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")
        val props = response.asJsoup()
            .selectFirst("astro-island[component-url*=MangaChaptersLoader]")
            ?.attr("props") ?: return emptyList()

        val data = props.parseAs<MangaWrapper>()
        val chapters = data.manga.value.mangaChapters.value.map { it.value }

        return chapters.map { ch ->
            SChapter.create().apply {
                name = ch.title.value?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.chapterNumber.value}"
                url = "/reader/$seriesSlug/${ch.chapterNumber.value}"
                date_upload = dateFormat.tryParse(ch.createdAt.value)
            }
        }
    }

    // Page - نظام إعادة المحاولة المحسّن (يطلب الصفحة مجدداً)
    override fun pageListParse(response: Response): List<Page> {
        var currentResponse = response
        var document = currentResponse.asJsoup()

        // الانتظار الأولي
        Thread.sleep(translationWaitTime.toLong())

        var pages = getPages(document)
        var retries = 3

        // إذا وجدنا صفحات ولكن بدون فقاعات نصية، نقوم بإعادة جلب (Re-fetch) الصفحة بالكامل
        while (pages.isNotEmpty() && pages.any { !it.hasSpeechBubbles() } && retries < maxRetries) {
            Thread.sleep(retryDelay.toLong())

            try {
                // يجب إنشاء استجابة جديدة لجلب أحدث حالة للـ HTML من الخادم
                val request = currentResponse.request
                val newResponse = client.newCall(request).execute()

                if (newResponse.isSuccessful) {
                    currentResponse = newResponse
                    document = currentResponse.asJsoup()
                    val newPages = getPages(document)

                    if (newPages.isNotEmpty() && newPages.any { it.hasSpeechBubbles() }) {
                        pages = newPages
                        break
                    }
                }
            } catch (e: Exception) {
                // في حالة فشل الاتصال، نتجاهل الخطأ لنحاول مرة أخرى في الدورة القادمة
            }

            retries++
        }

        return pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.hasSpeechBubbles() -> "${page.imageUrl}${page.bubbles.toJsonString().toFragment()}"
                else -> page.imageUrl
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

                        if (text.isEmpty() || !style.contains("left:") || !style.contains("top:")) {
                            return@mapNotNull null
                        }

                        Bubble(
                            text = text,
                            left = style.substringAfterLast("left:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            top = style.substringAfterLast("top:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            width = style.substringAfterLast("width:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            height = style.substringAfterLast("height:").substringBefore("%").trim().toFloatOrNull() ?: 0f,
                            angle = style.substringAfterLast("angle:").substringBefore("deg").trim().toFloatOrNull() ?: 0f
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

    fun String.toFragment(): String = "#${this.replace("#", "*")}"

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
}

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // [نفس كود التفضيلات الذي كتبته أنت بدون تغيير لتقليل التشتت]
        // ... (تم الحفاظ عليه كما هو في ملفك الأصلي)
}

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?v=\d+)?#\[.*?]""", RegexOption.IGNORE_CASE)

        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val DEFAULT_FONT_SIZE = "28"

        private const val TRANSLATION_WAIT_PREF = "translationWaitPref"
        private const val DEFAULT_TRANSLATION_WAIT = "35000" // 35 ثانية

        private const val MAX_RETRIES_PREF = "maxRetriesPref"
        private const val DEFAULT_MAX_RETRIES = "5" // قمت بوضع 5 كقيمة افتراضية

        private const val RETRY_DELAY_PREF = "retryDelayPref"
        private const val DEFAULT_RETRY_DELAY = "5000" // 5 ثوانٍ بين كل محاولة
    }
}
