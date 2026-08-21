package eu.kanade.tachiyomi.extension.ar.prochan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class ProchanFilters {
    companion object {
        fun getFilterList(): FilterList = FilterList(
            CategoryFilter(),
            StatusFilter(),
            SortFilter(),
            GenreFilter()
        )
    }

    class CategoryFilter : Filter.Select<String>(
        "النوع",
        arrayOf(
            "الكل",
            "مانهوا",
            "مانجا",
            "Webtoon",
            "Light Novel"
        ),
        state = 0
    )

    class StatusFilter : Filter.Select<String>(
        "الحالة",
        arrayOf(
            "الكل",
            "مستمر",
            "مكتمل",
            "متوقف مؤقتاً"
        ),
        state = 0
    )

    class SortFilter : Filter.Select<String>(
        "ترتيب حسب",
        arrayOf(
            "الأحدث",
            "الأكثر مشاهدة",
            "الأعلى تقييماً",
            "الأبجدي"
        ),
        state = 0
    )

    class GenreFilter : Filter.TriStateGroup(
        "الأنواع",
        listOf(
            Filter.TriState("أكشن"),
            Filter.TriState("مغامرة"),
            Filter.TriState("كوميديا"),
            Filter.TriState("درامي"),
            Filter.TriState("خيال"),
            Filter.TriState("رعب"),
            Filter.TriState("ايسيكاي"),
            Filter.TriState("غموض"),
            Filter.TriState("رومانسي"),
            Filter.TriState("مدرسي"),
            Filter.TriState("خيال علمي"),
            Filter.TriState("حياة يومية"),
            Filter.TriState("خارق للطبيعة"),
            Filter.TriState("نفسي"),
            Filter.TriState("رياضي"),
            Filter.TriState("شوجو"),
            Filter.TriState("شونين"),
            Filter.TriState("سيينين"),
            Filter.TriState("جوسي"),
            Filter.TriState("BL"),
            Filter.TriState("Yuri")
        )
    )
}
