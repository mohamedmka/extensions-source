package eu.kanade.tachiyomi.extension.ar.prochan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Prochan : ParsedHttpSource() {

    override val name = "ProChan"
    override val baseUrl = "https://procomic.pro/ar"
    override val lang = "ar"
    override val supportsLatest = true

    private val imageBaseUrl = "https://app.procomic.pro"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/series?page=$page", headers)

    override fun popularMangaSelector() = "a[href*='/ar/'][href!='/ar/']"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href").removePrefix(baseUrl)
            title = element.text().trim()

            thumbnail_url = element.selectFirst("img")?.let { img ->
                img.absUrl("src").ifBlank {
                    img.absUrl("data-src")
                }
            }
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel='next']"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/updates?page=$page", headers)

    override fun latestUpdatesSelector() = "a[href*='/chapter/']"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href").removePrefix(baseUrl)
            title = element.text().trim()
        }
    }

    override fun latestUpdatesNextPageSelector() = "a[rel='next']"

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ) = GET(
        "$baseUrl/series?search=${URLEncoder.encode(query, "UTF-8")}&page=$page",
        headers,
    )

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "a[rel='next']"

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()?.trim() ?: ""

            description = document
                .selectFirst("meta[name='description']")
                ?.attr("content")
                ?.trim()

            thumbnail_url = document.selectFirst("img")?.let { img ->
                img.absUrl("src").ifBlank {
                    img.absUrl("data-src")
                }
            }

            status = SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "a[href*='/chapter/']"

    override fun chapterFromElement(element: Element): SChapter {
        val name = element.text().trim()

        return SChapter.create().apply {
            url = element.attr("href").removePrefix(baseUrl)
            this.name = name

            chapter_number = Regex(
                """(?:الفصل|chapter|ch\.?)\s*(\d+(?:\.\d+)?)""",
                RegexOption.IGNORE_CASE,
            )
                .find(name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()
                ?: Regex("""\d+(?:\.\d+)?""")
                    .find(name)
                    ?.value
                    ?.toFloatOrNull()
                    ?: 0f
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val imageUrls = document
            .select("img[src], img[data-src], source[src], source[srcset]")
            .flatMap { element ->
                buildList {
                    val src = element.absUrl("src")
                    if (src.isNotBlank()) {
                        add(src)
                    }

                    val dataSrc = element.absUrl("data-src")
                    if (dataSrc.isNotBlank()) {
                        add(dataSrc)
                    }

                    val srcSet = element.attr("srcset")
                    if (srcSet.isNotBlank()) {
                        srcSet
                            .split(",")
                            .map { it.trim().substringBefore(" ") }
                            .filter { it.isNotBlank() }
                            .forEach { add(it) }
                    }
                }
            }
            .filter {
                it.startsWith(imageBaseUrl) && it.contains("/chapters/")
            }
            .distinct()

        return imageUrls.mapIndexed { index, url ->
            Page(
                index = index,
                imageUrl = url,
            )
        }
    }

    override fun imageUrlParse(document: Document): String? {
        return document
            .select("img[src], img[data-src]")
            .map { img ->
                img.absUrl("src").ifBlank {
                    img.absUrl("data-src")
                }
            }
            .firstOrNull {
                it.startsWith(imageBaseUrl)
            }
    }

    override fun getFilterList(): FilterList = FilterList()
}
