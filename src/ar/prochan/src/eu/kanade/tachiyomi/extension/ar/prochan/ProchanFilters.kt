package eu.kanade.tachiyomi.extension.ar.prochan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class ProchanFilters {
    companion object {
        fun getFilterList(): FilterList = FilterList(
            TypeFilter(),
            StatusFilter(),
            GenreFilter()
        )
    }

    class TypeFilter : Filter.Select<String>(
        "النوع",
        arrayOf("الكل", "مانهوا", "مانجا", "Webtoon")
    )

    class StatusFilter : Filter.Select<String>(
        "الحالة",
        arrayOf("الكل", "مستمر", "مكتمل")
    )

    class GenreFilter : Filter.TriStateGroup(
        "الأنواع",
        listOf(
            Filter.TriState("Action"),
            Filter.TriState("Adventure"),
            Filter.TriState("Comedy"),
            Filter.TriState("Drama"),
            Filter.TriState("Fantasy"),
            Filter.TriState("Horror"),
            Filter.TriState("Isekai"),
            Filter.TriState("Mystery"),
            Filter.TriState("Romance"),
            Filter.TriState("School"),
            Filter.TriState("Sci-Fi"),
            Filter.TriState("Slice of Life"),
            Filter.TriState("Supernatural")
        )
    )
}
