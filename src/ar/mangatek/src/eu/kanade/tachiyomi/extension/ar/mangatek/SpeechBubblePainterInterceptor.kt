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
import eu.kanade.tachiyomi.extension.ar.mangatek.MangaTek.Companion.PAGE_REGEX
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class SpeechBubblePainterInterceptor : Interceptor {

    // Simple in-memory cache to avoid repeated translation calls for same text
    private val translationCache = ConcurrentHashMap<String, String>()

    @Serializable
    private data class TranslateReq(val q: String, val source: String = "auto", val target: String, val format: String = "text")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val speechBubbles = request.url.fragment?.parseAs<List<Bubble>>().orEmpty()

        val response = chain.proceed(request.newBuilder().url(url).build())
        if (!response.isSuccessful || speechBubbles.isEmpty()) {
            return response
        }

        // Pre-translate bubble texts (synchronously). If translation disabled or not configured, we keep original.
        if (MangaTek.TRANSLATION_ENABLED && MangaTek.TRANSLATION_URL.isNotBlank()) {
            val httpClient = okhttp3.OkHttpClient.Builder().build()
            for (bubble in speechBubbles) {
                val original = bubble.text
                val cached = translationCache[original]
                if (cached != null) {
                    bubble.translatedText = cached
                    continue
                }

                try {
                    val payload = Json.encodeToString(TranslateReq.serializer(), TranslateReq(original, target = MangaTek.TRANSLATION_TARGET))
                    val body = payload.toRequestBody("application/json".toMediaType())
                    val reqBuilder = Request.Builder()
                        .url(MangaTek.TRANSLATION_URL)
                        .post(body)

                    MangaTek.TRANSLATION_API_KEY?.let { key ->
                        // Many services expect Authorization: Bearer <key> but adjust if your provider differs
                        reqBuilder.header("Authorization", "Bearer $key")
                    }

                    val transReq = reqBuilder.build()
                    httpClient.newCall(transReq).execute().use { transResp ->
                        if (transResp.isSuccessful) {
                            val text = transResp.body?.string().orEmpty()
                            val elem = Json.parseToJsonElement(text)

                            // Try common shapes: { "translatedText": "..." } OR { "data": { "translations": [ {"translatedText":"..."} ] } } OR { "translated_text": "..." }
                            val translated: String? = when {
                                elem.jsonObject["translatedText"] != null -> elem.jsonObject["translatedText"]!!.jsonPrimitive.content
                                elem.jsonObject["translated_text"] != null -> elem.jsonObject["translated_text"]!!.jsonPrimitive.content
                                elem.jsonObject["result"] != null -> elem.jsonObject["result"]!!.jsonPrimitive.content
                                elem.jsonObject["data"]?.jsonObject?.get("translations")?.jsonArray?.getOrNull(0)?.jsonObject?.get("translatedText") != null ->
                                    elem.jsonObject["data"]!!.jsonObject["translations"]!!.jsonArray[0].jsonObject["translatedText"]!!.jsonPrimitive.content
                                else -> null
                            }

                            if (!translated.isNullOrBlank()) {
                                bubble.translatedText = translated
                                translationCache[original] = translated
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore translation failures and draw original text
                }
            }
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream(), null, options)!!

        val canvas = Canvas(bitmap)

        val textPaint = TextPaint().apply {
            isAntiAlias = true
        }
        speechBubbles.forEach { speechBubble ->
            val pxX = speechBubble.x
            val pxY = speechBubble.y
            val pxWidth = speechBubble.w
            val pxHeight = speechBubble.h
            val pxCenterY = pxY + (pxHeight / 2f)

            // If AI translation exists, use it, otherwise original text
            val drawText = speechBubble.translatedText ?: speechBubble.text

            textPaint.color = Color.parseColor(speechBubble.color)
            // bgColor is used in original implementation for stroke background; keep usage (extension may exist)
            textPaint.bgColor = Color.parseColor(speechBubble.strokeColor)
            textPaint.textSize = speechBubble.fontSizePx
            textPaint.strokeWidth = speechBubble.strokeWidthPx

            // create a temporary Bubble instance with translated text but same layout params
            val drawBubble = Bubble(
                drawText,
                speechBubble.x,
                speechBubble.y,
                speechBubble.w,
                speechBubble.h,
                speechBubble.angle,
                speechBubble.color,
                speechBubble.strokeColor,
                speechBubble.fontSizePx,
                speechBubble.lineHeight,
                speechBubble.strokeWidthPx,
            )

            val bubbleLayout = createBubble(pxHeight, pxWidth, drawBubble, textPaint)
            val finalY = getYAxis(pxY, pxHeight, pxCenterY, textPaint, bubbleLayout)
            canvas.draw(textPaint, bubbleLayout, speechBubble.angle, pxX, finalY)
        }

        val ext = url.substringBefore("#")
            .substringBefore("?")
            .substringAfterLast(".")
            .lowercase()
        val format = when (ext) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }

        val output = ByteArrayOutputStream().use { stream ->
            bitmap.compress(format, 100, stream)
            stream.toByteArray()
        }

        bitmap.recycle()

        return response.newBuilder()
            .body(output.toResponseBody(mediaType))
            .build()
    }

    private fun getYAxis(
        pxY: Float,
        pxHeight: Float,
        pxCenterY: Float,
        textPaint: TextPaint,
        bubble: StaticLayout,
    ): Float {
        val fontHeight = textPaint.fontMetrics.let { it.bottom - it.top }
        val dialogBoxLineCount = pxHeight / fontHeight
        return when {
            bubble.lineCount < dialogBoxLineCount -> pxCenterY - (bubble.lineCount / 2f) * fontHeight
            else -> pxY
        }
    }

    private fun createBubble(
        pxHeight: Float,
        pxWidth: Float,
        dialog: Bubble,
        textPaint: TextPaint,
    ): StaticLayout {
        var bubble = createBubbleLayout(pxWidth, dialog, textPaint)

        if (bubble.height <= pxHeight) {
            return bubble
        }

        while (bubble.height > pxHeight) {
            textPaint.textSize -= 0.5f
            bubble = createBubbleLayout(pxWidth, dialog, textPaint)
        }

        return bubble
    }

    private fun createBubbleLayout(pxWidth: Float, dialog: Bubble, textPaint: TextPaint): StaticLayout {
        val text = dialog.text

        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, pxWidth.toInt()).apply {
            setAlignment(Layout.Alignment.ALIGN_CENTER)
            setIncludePad(true)
            setLineSpacing(0f, dialog.lineHeight.coerceAtLeast(0.5f))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            }
        }.build()
    }

    private fun Canvas.draw(textPaint: TextPaint, layout: StaticLayout, angle: Float, x: Float, y: Float) {
        save()
        translate(x, y)
        rotate(angle)
        drawTextOutline(textPaint, layout)
        drawText(textPaint, layout)
        restore()
    }

    private fun Canvas.drawText(textPaint: TextPaint, layout: StaticLayout) {
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = 0f
        layout.draw(this)
    }

    private fun Canvas.drawTextOutline(textPaint: TextPaint, layout: StaticLayout) {
        val foregroundColor = textPaint.color
        val style = textPaint.style

        textPaint.color = textPaint.bgColor
        textPaint.style = Paint.Style.FILL_AND_STROKE

        layout.draw(this)

        textPaint.color = foregroundColor
        textPaint.style = style
    }

    companion object {
        val mediaType = "image/png".toMediaType()
    }
}