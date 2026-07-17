package eu.kanade.tachiyomi.extension.ar.anyonemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
class AnyoneManga : Madara("AnyoneManga", "https://anyonemanga.com", "ar") {

    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: Element): String? {
        // 1. التحقق من وجود صورة مشفرة أو بصيغة Base64 (بدون استخدام abs:)
        val encryptedSrc = element.attr("data-encrypted-src")
        if (encryptedSrc.isNotBlank()) {
            return encryptedSrc
        }

        // 2. بدائل احتياطية في حال قام الموقع بتغيير طريقة عرض الصور
        return element.attr("data-src").takeIf { it.isNotBlank() }
            ?: element.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: element.attr("abs:src")
    }
}
