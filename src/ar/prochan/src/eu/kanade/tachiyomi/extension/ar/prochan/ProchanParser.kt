package eu.kanade.tachiyomi.extension.ar.prochan

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProchanParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ==================== Manga Parsing ====================
    fun parseMangaFromJson(jsonStr: String): SManga? {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            val manga = jsonElement["data"]?.jsonObject ?: return null

            SManga.create().apply {
                title = manga["title"]?.jsonPrimitive?.content ?: "Unknown"
                url = "/manga/${manga["id"]?.jsonPrimitive?.content ?: "0"}-${
                    manga["slug"]?.jsonPrimitive?.content ?: "unknown"
                }"
                thumbnail_url = manga["coverUrl"]?.jsonPrimitive?.content ?: ""
                author = manga["author"]?.jsonPrimitive?.content ?: ""
                artist = manga["artist"]?.jsonPrimitive?.content ?: ""
                description = manga["description"]?.jsonPrimitive?.content ?: ""
                status = when (manga["status"]?.jsonPrimitive?.content?.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
                genre = manga["genres"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                }?.joinToString(", ") ?: ""
                initialized = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseMangaListFromJson(jsonStr: String): List<SManga> {
        val mangas = mutableListOf<SManga>()
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            val dataArray = jsonElement["data"]?.jsonArray ?: return mangas

            dataArray.forEach { mangaElement ->
                val manga = mangaElement.jsonObject
                mangas.add(
                    SManga.create().apply {
                        title = manga["title"]?.jsonPrimitive?.content ?: "Unknown"
                        url = "/manga/${manga["id"]?.jsonPrimitive?.content ?: "0"}-${
                            manga["slug"]?.jsonPrimitive?.content ?: "unknown"
                        }"
                        thumbnail_url = manga["coverUrl"]?.jsonPrimitive?.content ?: ""
                        author = manga["author"]?.jsonPrimitive?.content ?: ""
                        description = manga["description"]?.jsonPrimitive?.content ?: ""
                        status = when (manga["status"]?.jsonPrimitive?.content?.lowercase()) {
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
            mangas
        } catch (e: Exception) {
            e.printStackTrace()
            mangas
        }
    }

    // ==================== Chapter Parsing ====================
    fun parseChaptersFromJson(jsonStr: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            val chaptersArray = jsonElement["data"]?.jsonArray ?: return chapters

            chaptersArray.forEachIndexed { index, chapterElement ->
                val chapter = chapterElement.jsonObject
                chapters.add(
                    SChapter.create().apply {
                        name = chapter["title"]?.jsonPrimitive?.content ?: "الفصل $index"
                        url = "/chapter/${chapter["id"]?.jsonPrimitive?.content ?: ""}"
                        chapter_number = chapter["number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                        date_upload = try {
                            val dateStr = chapter["uploadDate"]?.jsonPrimitive?.content ?: ""
                            ProchanUtils.parseDate(dateStr)
                        } catch (e: Exception) {
                            0L
                        }
                        scans = chapter["scans"]?.jsonPrimitive?.content ?: ""
                    }
                )
            }
            chapters.reversed()
        } catch (e: Exception) {
            e.printStackTrace()
            chapters
        }
    }

    // ==================== Page Parsing ====================
    fun parsePagesFromJson(jsonStr: String): List<String> {
        val pages = mutableListOf<String>()
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            val pagesArray = jsonElement["data"]?.jsonArray ?: return pages

            pagesArray.forEach { pageElement ->
                val page = pageElement.jsonObject
                val imageUrl = page["imageUrl"]?.jsonPrimitive?.content
                if (!imageUrl.isNullOrEmpty() && ProchanUtils.isValidImageUrl(imageUrl)) {
                    pages.add(imageUrl)
                }
            }
            pages
        } catch (e: Exception) {
            e.printStackTrace()
            pages
        }
    }

    // ==================== Search Parsing ====================
    fun parseSearchResultsFromJson(jsonStr: String): List<SManga> {
        return parseMangaListFromJson(jsonStr)
    }

    // ==================== Trending/Popular Parsing ====================
    fun parseTrendingFromJson(jsonStr: String): Pair<List<SManga>, List<SManga>> {
        val trending = mutableListOf<SManga>()
        val popular = mutableListOf<SManga>()

        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject

            // Parse Trending
            jsonElement["trending"]?.jsonArray?.forEach { element ->
                trending.add(parseMangaElement(element.jsonObject))
            }

            // Parse Popular
            jsonElement["popular"]?.jsonArray?.forEach { element ->
                popular.add(parseMangaElement(element.jsonObject))
            }

            Pair(trending, popular)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(trending, popular)
        }
    }

    // ==================== Helper Methods ====================
    private fun parseMangaElement(manga: Any): SManga {
        val mangaObj = manga as? kotlinx.serialization.json.JsonObject ?: return SManga.create()
        return SManga.create().apply {
            title = mangaObj["title"]?.jsonPrimitive?.content ?: "Unknown"
            url = "/manga/${mangaObj["id"]?.jsonPrimitive?.content ?: "0"}"
            thumbnail_url = mangaObj["coverUrl"]?.jsonPrimitive?.content ?: ""
            author = mangaObj["author"]?.jsonPrimitive?.content ?: ""
            status = when (mangaObj["status"]?.jsonPrimitive?.content?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ==================== Validation ====================
    fun isValidJsonResponse(jsonStr: String): Boolean {
        return try {
            Json.parseToJsonElement(jsonStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasError(jsonStr: String): Boolean {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            jsonElement.containsKey("error")
        } catch (e: Exception) {
            false
        }
    }

    fun getErrorMessage(jsonStr: String): String? {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
            jsonElement["message"]?.jsonPrimitive?.content
                ?: jsonElement["error"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
