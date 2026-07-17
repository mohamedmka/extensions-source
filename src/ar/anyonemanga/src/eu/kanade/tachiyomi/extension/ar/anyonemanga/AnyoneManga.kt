package eu.kanade.tachiyomi.extension.ar.anyonemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class AnyoneManga : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: Element): String? {
        // التحقق من وجود صورة بصيغة Base64 أو مشفرة بدون استخدام abs: التي كانت تسبب انهيار محرك الصور
        val encryptedSrc = element.attr("data-encrypted-src")
        if (encryptedSrc.isNotBlank()) {
            return encryptedSrc
        }

        // بدائل احتياطية قياسية في حال تم تغيير سمات عرض الصور
        return element.attr("data-src").takeIf { it.isNotBlank() }
            ?: element.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: element.attr("abs:src")
    }
}
