package eu.kanade.tachiyomi.extension.ar.prochan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== Manga DTOs ====================
@Serializable
data class MangaListResponse(
    @SerialName("data")
    val data: List<MangaDto>,
    @SerialName("hasNextPage")
    val hasNextPage: Boolean,
    @SerialName("currentPage")
    val currentPage: Int? = 1,
    @SerialName("totalPages")
    val totalPages: Int? = 1
)

@Serializable
data class MangaDto(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("slug")
    val slug: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("coverUrl")
    val coverUrl: String,
    @SerialName("author")
    val author: String? = null,
    @SerialName("artist")
    val artist: String? = null,
    @SerialName("status")
    val status: String? = "ongoing",
    @SerialName("genres")
    val genres: List<String>? = emptyList(),
    @SerialName("rating")
    val rating: Float? = null,
    @SerialName("views")
    val views: Long? = 0,
    @SerialName("chapterCount")
    val chapterCount: Int? = 0,
    @SerialName("latestChapter")
    val latestChapter: String? = null,
    @SerialName("latestChapterDate")
    val latestChapterDate: String? = null
)

@Serializable
data class MangaDetailsResponse(
    @SerialName("data")
    val data: MangaDetailDto
)

@Serializable
data class MangaDetailDto(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("slug")
    val slug: String,
    @SerialName("description")
    val description: String,
    @SerialName("coverUrl")
    val coverUrl: String,
    @SerialName("author")
    val author: String,
    @SerialName("artist")
    val artist: String,
    @SerialName("status")
    val status: String,
    @SerialName("genres")
    val genres: List<String>,
    @SerialName("rating")
    val rating: Float,
    @SerialName("views")
    val views: Long,
    @SerialName("yearOfRelease")
    val yearOfRelease: Int? = null,
    @SerialName("chapters")
    val chapters: List<ChapterDto> = emptyList(),
    @SerialName("characters")
    val characters: List<String>? = emptyList(),
    @SerialName("relatedManga")
    val relatedManga: List<Int>? = emptyList()
)

// ==================== Chapter DTOs ====================
@Serializable
data class ChapterListResponse(
    @SerialName("data")
    val data: List<ChapterDto>,
    @SerialName("hasNextPage")
    val hasNextPage: Boolean? = false
)

@Serializable
data class ChapterDto(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("number")
    val number: Float,
    @SerialName("slug")
    val slug: String? = null,
    @SerialName("uploadDate")
    val uploadDate: String,
    @SerialName("scans")
    val scans: String? = null,
    @SerialName("views")
    val views: Long? = 0
)

// ==================== Page DTOs ====================
@Serializable
data class PageListResponse(
    @SerialName("data")
    val data: List<PageDto>
)

@Serializable
data class PageDto(
    @SerialName("number")
    val number: Int,
    @SerialName("imageUrl")
    val imageUrl: String,
    @SerialName("page")
    val page: Int? = null
)

// ==================== Search Response ====================
@Serializable
data class SearchResponse(
    @SerialName("results")
    val results: List<MangaDto>,
    @SerialName("total")
    val total: Int,
    @SerialName("hasNextPage")
    val hasNextPage: Boolean
)

// ==================== Error Response ====================
@Serializable
data class ErrorResponse(
    @SerialName("error")
    val error: String,
    @SerialName("message")
    val message: String? = null,
    @SerialName("code")
    val code: Int? = null
)

// ==================== Trending/Popular Response ====================
@Serializable
data class TrendingResponse(
    @SerialName("trending")
    val trending: List<MangaDto>,
    @SerialName("popular")
    val popular: List<MangaDto>,
    @SerialName("recently_updated")
    val recentlyUpdated: List<MangaDto>
)
