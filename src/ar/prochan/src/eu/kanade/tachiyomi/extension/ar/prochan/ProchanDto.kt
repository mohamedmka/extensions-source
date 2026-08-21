package eu.kanade.tachiyomi.extension.ar.prochan

import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val data: List<MangaDto>,
    val hasNextPage: Boolean
)

@Serializable
data class MangaDto(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val coverUrl: String,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = "ongoing",
    val genres: List<String>? = emptyList(),
    val rating: Float? = null,
    val chapterCount: Int? = 0
)

@Serializable
data class MangaDetailsDto(
    val data: MangaDto
)

@Serializable
data class ChapterListDto(
    val data: List<ChapterDto>
)

@Serializable
data class ChapterDto(
    val id: Int,
    val title: String,
    val number: Float,
    val uploadDate: String,
    val scans: String? = null
)

@Serializable
data class PageListDto(
    val data: List<PageDto>
)

@Serializable
data class PageDto(
    val number: Int,
    val imageUrl: String
)
