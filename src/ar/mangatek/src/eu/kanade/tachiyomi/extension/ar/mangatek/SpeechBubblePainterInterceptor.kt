package eu.kanade.tachiyomi.extension.ar.mangatek

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.extension.ar.mangatek.MangaTek.Companion.PAGE_REGEX
import java.io.ByteArrayOutputStream
import keiyoushi.utils.parseAs
import kotlin.math.max
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.Jsoup

@RequiresApi(Build.VERSION_CODES.O)
class SpeechBubblePainterInterceptor(
    val fontSize: Int,
    val enableDarkMode: Boolean = true,
    private val httpClient: OkHttpClient // تمرير OkHttpClient لعمل طلبات الـ API
) : Interceptor {

    private val startTime = System.currentTimeMillis()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        PerformanceMonitor.startTimer()
        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>() ?: emptyList()
        val imageRequest = request.newBuilder().url(url).build()
        val response = chain.proceed(imageRequest)

        if (response.isSuccessful.not()) {
            LoggerService.warning("Failed to load image: ${response.code}")
            return response
        }

        try {
            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())!!
                .copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            // معالجة الفقاعات مع الذكاء الاصطناعي
            if (speechBubbles.isNotEmpty()) {
                drawSpeechBubbles(canvas, speechBubbles, imageWidth, imageHeight, fontSize)
                PerformanceMonitor.recordTiming("drawSpeechBubbles")
            }

            val output = ByteArrayOutputStream()
            val ext = url.substringBefore("#").substringAfterLast(".").lowercase()
            val format = when (ext) {
                "png" -> Bitmap.CompressFormat.PNG
                "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
                else -> Bitmap.CompressFormat.WEBP
            }

            bitmap.compress(format, 100, output)

            val responseBody = output.toByteArray().toResponseBody(mediaType)
            LoggerService.info("Image processed successfully with ${speechBubbles.size} bubbles")
            return response.newBuilder()
                .body(responseBody)
                .build()
        } catch (e: Exception) {
            LoggerService.error("Error processing image", e)
            return response
        }
    }

    /**
     * رسم الفقاعات النصية مع معالجة ذكية للترجمات وجلبها عبر AI
     */
    private fun drawSpeechBubbles(
        canvas: Canvas,
        speechBubbles: List<Bubble>,
        imageWidth: Float,
        imageHeight: Float,
        fontSize: Int
    ) {
        var processedCount = 0
        var failedCount = 0

        speechBubbles.forEachIndexed { index, speechBubble ->
            try {
                if (!isValidBubble(speechBubble)) {
                    failedCount++
                    LoggerService.warning("Invalid bubble at index $index")
                    return@forEachIndexed
                }

                val pxX = (speechBubble.left / 100f) * imageWidth
                val pxY = (speechBubble.top / 100f) * imageHeight
                val pxWidth = (speechBubble.width / 100f) * imageWidth
                val pxHeight = (speechBubble.height / 100f) * imageHeight
                val pxCenterY = pxY + (pxHeight / 2f)

                val detectedType = speechBubble.type.takeIf { it != "normal" } ?: speechBubble.detectBubbleType()
                val detectedDirection = speechBubble.direction ?: speechBubble.detectDirection()

                val cacheKey = "${speechBubble.text.hashCode()}_$detectedType"
                var cleanText = TranslationCache.get(cacheKey)

                // إذا لم تكن الترجمة في الذاكرة المؤقتة، قم بجلبها من الذكاء الاصطناعي
                if (cleanText == null) {
                    LoggerService.info("Fetching AI translation for bubble $index")
                    val aiTranslation = fetchAiTranslationWithRetry(speechBubble.text)

                    // معالجة ذكية للنص: تنظيف وتحسين الترجمة بعد جلبها
                    cleanText = processTranslationText(aiTranslation)

                    // حفظ في الذاكرة المؤقتة
                    if (cleanText.isNotEmpty()) {
                        TranslationCache.put(cacheKey, cleanText)
                    }
                } else {
                    LoggerService.info("Using cached translation for pointer $index")
                }

                if (cleanText.isNullOrEmpty()) {
                    failedCount++
                    return@forEachIndexed
                }

                val textPaint = createTextPaint(fontSize, speechBubble.getTextColor(), detectedType)
                val bgColor = speechBubble.getBackgroundColor()

                val bubble = createBubbleWithIntelligentSizing(
                    pxHeight,
                    pxWidth,
                    cleanText,
                    speechBubble.angle,
                    textPaint,
                    detectedType
                )

                val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubble)

                drawBubbleBackground(canvas, pxX, finalY, bubble, speechBubble.angle, pxWidth, pxHeight, bgColor)
                canvas.draw(textPaint, bubble, speechBubble.angle, pxX, finalY)

                processedCount++
                LoggerService.info("Processed bubble $index: type=$detectedType, direction=$detectedDirection")
            } catch (e: Exception) {
                failedCount++
                LoggerService.error("Error processing bubble at index $index", e, index)
            }
        }

        TranslationCache.updateStats(processedCount, failedCount, 0)
        LoggerService.info("Completed: $processedCount processed, $failedCount failed out of ${speechBubbles.size} total")
    }

    /**
     * آلية جلب الترجمة من الذكاء الاصطناعي مع نظام المحاولات المتكررة (Retries)
     */
    private fun fetchAiTranslationWithRetry(originalText: String): String {
        var attempts = 0
        while (attempts < MAX_TRANSLATION_RETRIES) {
            try {
                // ضع رابط API الذكاء الاصطناعي الخاص بك هنا (مثل ChatGPT, DeepL, أو خادمك الخاص)
                val requestUrl = "https://api.your-ai-service.com/translate?text=${originalText.trim()}"

                val request = Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", "Bearer YOUR_API_KEY_HERE")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""

                    // افترض أن الاستجابة بصيغة JSON وتحتوي على حقل "translated_text"
                    val json = JSONObject(responseBody)
                    return json.optString("translated_text", originalText)
                } else {
                    LoggerService.warning("AI Translation API returned code: ${response.code}")
                }
            } catch (e: Exception) {
                LoggerService.warning("AI Translation attempt ${attempts + 1} failed: ${e.message}")
            }

            attempts++
            if (attempts < MAX_TRANSLATION_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS) // الانتظار قبل المحاولة التالية
            }
        }

        // إذا فشلت كل المحاولات، قم بإرجاع النص الأصلي أو نص فارغ
        return originalText
    }

    // ... [باقي الدوال مثل isValidBubble و processTranslationText تبقى كما هي في الكود الخاص بك] ...

    private fun Canvas.drawTextOutline(textPaint: TextPaint, layout: StaticLayout) {
        val foregroundColor = textPaint.color
        val style = textPaint.style
        textPaint.strokeWidth = 5F
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL_AND_STROKE
        layout.draw(this)
        textPaint.color = foregroundColor
        textPaint.style = style // تم تصحيح الخطأ الإملائي هنا (كانت sty le)
    }

    // ... [باقي الكود] ...

    companion object {
        const val SCALED_DENSITY = 0.75f
        const val MIN_FONT_SIZE = 6f
        val mediaType = "image/png".toMediaType()

        const val AI_TRANSLATION_WAIT_MS = 60000L
        const val MAX_TRANSLATION_RETRIES = 5 // قللت العدد لـ 5 لكي لا يطول الانتظار بشكل كبير
        const val RETRY_DELAY_MS = 3000L // تم تعديلها ل 3 ثوان كفترة انتظار معقولة
    }
}
