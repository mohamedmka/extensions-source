package eu.kanade.tachiyomi.extension.ar.prochan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Prochan : ParsedHttpSource() {

    override val name = "ProChan"
    override val baseUrl = "https://procomic.pro/ar"
    override val lang = "ar"
    override val supportsLatest = true

    // أشهر المانهوا
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/series?page=$page", headers)

    override fun popularMangaSelector() =
        "a[href*='/ar/']"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href")
                .removePrefix(baseUrl)

            title = element.text().trim()

            thumbnail_url = element
                .selectFirst("img")
                ?.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector() =
        "a[rel='next']"

    // آخر الفصول
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/updates?page=$page", headers)

    override fun latestUpdatesSelector() =
        "a[href*='/chapter/']"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href")
                .substringBeforeLast("/")
                .removePrefix(baseUrl)

            title = element.text().trim()
        }
    }

    override fun latestUpdatesNextPageSelector() =
        "a[rel='next']"

    // البحث
    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList
    ) = GET(
        "$baseUrl/series?search=$query&page=$page",
        headers
    )

    override fun searchMangaSelector() =
        popularMangaSelector()

    override fun searchMangaFromElement(
        element: Element
    ) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() =
        "a[rel='next']"

    // معلومات المانهوا
    override fun mangaDetailsParse(
        document: Document
    ): SManga {

        return SManga.create().apply {

            title = document
                .selectFirst("h1")
                ?.text()
                ?.trim()
                ?: ""

            description = document
                .selectFirst("meta[name=description]")
                ?.attr("content")

            thumbnail_url = document
                .selectFirst("img")
                ?.absUrl("src")

            status = SManga.UNKNOWN
        }
    }

    // الفصول
    override fun chapterListSelector() =
        "a[href*='/chapter/']"

    override fun chapterFromElement(
        element: Element
    ): SChapter {

        val name = element.text().trim()

        return SChapter.create().apply {

            url = element.attr("href")
                .removePrefix(baseUrl)

            this.name = name

            chapter_number = Regex(
                """\d+(?:\.\d+)?"""
            )
                .find(name)
                ?.value
                ?.toFloatOrNull()
                ?: 0f
        }
    }

    // صفحات الفصل
    override fun pageListParse(
        document: Document
    ): List<Page> {

        return document
            .select("img")
            .mapIndexedNotNull { index, img ->

                val url = img
                    .absUrl("src")
                    .ifBlank {
                        img.absUrl("data-src")
                    }

                if (url.isBlank()) {
                    null
                } else {
                    Page(
                        index = index,
                        imageUrl = url
                    )
                }
            }
}

    override fun imageUrlParse(
        document: Document
    ): String? = null

    override fun getFilterList() =
        FilterList()
}
