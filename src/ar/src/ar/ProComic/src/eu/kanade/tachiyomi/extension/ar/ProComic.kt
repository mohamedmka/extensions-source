package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class Procomic : HttpSource() {
    
    // ============ إعدادات أساسية ============
    override val name = "ProComic"
    override val baseUrl = "https://procomic.pro/ar"
    override val lang = "ar"
    override val supportsLatest = true
    
    override val client: OkHttpClient = network.cloudflareClient
    
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept-Language", "ar-SA,ar;q=0.9")
    
    // ============ البحث ============
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        
        return GET(url.toString(), headers)
    }
    
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        
        // TODO: عدّل الـ selectors حسب بنية ProComic الفعلية
        document.select("div.manga-card, div.comic-item").forEach { element ->
            val manga = SManga()
            
            // استخراج العنوان
            manga.title = element.select("h3, h4, .title").text().trim()
            
            // استخراج الرابط
            val link = element.select("a").attr("href")
            manga.url = if (link.startsWith("http")) link else "$baseUrl$link"
            
            // استخراج الصورة
            manga.thumbnail_url = element.select("img").attr("src").let { img ->
                if (img.startsWith("http")) img else "$baseUrl$img"
            }
            
            // استخراج الحالة (إذا كانت موجودة)
            manga.status = when {
                element.text().contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
                element.text().contains("مستمر", ignoreCase = true) -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            
            if (manga.title.isNotBlank()) {
                mangas.add(manga)
            }
        }
        
        // تحقق من وجود صفحة التالية
        val hasNextPage = document.select("a.next, a[rel=next], .pagination a.next").isNotEmpty()
        
        return MangasPage(mangas, hasNextPage)
    }
    
    // ============ أحدث المانهوا ============
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/latest".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        
        return GET(url.toString(), headers)
    }
    
    override fun latestUpdatesParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }
    
    // ============ تفاصيل المانهوا ============
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga()
        
        // العنوان
        manga.title = document.select("h1, .manga-title, .comic-title").text().trim()
        
        // الصورة
        manga.thumbnail_url = document.select("img.cover, img.poster, .manga-cover img").attr("src").let { img ->
            if (img.startsWith("http")) img else "$baseUrl$img"
        }
        
        // الوصف
        manga.description = document.select("p.description, .synopsis, .summary, .plot").text().trim()
        
        // المؤلف
        manga.author = document.select("span.author, .creator, [data-type=author]").text().trim()
        
        // الفنان
        manga.artist = document.select("span.artist, .illustrator").text().trim()
        
        // الفئة/النوع
        val genres = document.select("a.genre, .tag, .category").map { it.text().trim() }
        manga.genre = genres.joinToString(", ")
        
        // الحالة
        manga.status = when {
            document.text().contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
            document.text().contains("مستمر", ignoreCase = true) -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        
        return manga
    }
    
    // ============ قائمة الفصول ============
    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        
        // TODO: عدّل الـ selectors حسب بنية ProComic الفعلية
        document.select("div.chapter-item, li.chapter, tr.chapter").forEach { element ->
            val chapter = SChapter()
            
            // رقم/اسم الفصل
            chapter.name = element.select("h3, h4, .chapter-title, td:first-child").text().trim()
            
            // رابط الفصل
            val link = element.select("a").attr("href")
            chapter.url = if (link.startsWith("http")) link else "$baseUrl$link"
            
            // تاريخ النشر (اختياري)
            val dateText = element.select("span.date, .date, td:last-child").text().trim()
            chapter.date_upload = parseDate(dateText)
            
            // رقم الفصل (للترتيب)
            chapter.chapter_number = extractChapterNumber(chapter.name)
            
            if (chapter.name.isNotBlank()) {
                chapters.add(chapter)
            }
        }
        
        // اقلب الترتيب إذا كانت الفصول بالعكس
        return chapters.reversed()
    }
    
    // ============ صور الفصل ============
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }
    
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        
        // TODO: عدّل الـ selectors حسب بنية ProComic الفعلية
        document.select("img.page, img.chapter-image, div.page img").forEachIndexed { index, element ->
            val imageUrl = element.attr("src").let { img ->
                if (img.startsWith("http")) img else "$baseUrl$img"
            }
            
            if (imageUrl.isNotBlank()) {
                pages.add(Page(index, imageUrl = imageUrl))
            }
        }
        
        return pages
    }
    
    override fun imageUrlParse(response: Response): String {
        // يُستخدم عادة للصور المحمية أو المعالجة الخاصة
        return response.request.url.toString()
    }
    
    // ============ دوال مساعدة ============
    
    private fun parseDate(dateText: String): Long {
        return try {
            when {
                dateText.contains("اليوم", ignoreCase = true) -> System.currentTimeMillis()
                dateText.contains("أمس", ignoreCase = true) -> System.currentTimeMillis() - 86400000
                dateText.contains("ساعة", ignoreCase = true) -> System.currentTimeMillis()
                else -> {
                    // حاول تحليل التاريخ بصيغة عادية
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale("ar"))
                    sdf.parse(dateText)?.time ?: 0
                }
            }
        } catch (e: Exception) {
            0
        }
    }
    
    private fun extractChapterNumber(name: String): Float {
        return try {
            val regex = Regex("""(\d+(?:\.\d+)?)""")
            regex.find(name)?.value?.toFloat() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    override fun getFilterList(): FilterList = FilterList()
}
