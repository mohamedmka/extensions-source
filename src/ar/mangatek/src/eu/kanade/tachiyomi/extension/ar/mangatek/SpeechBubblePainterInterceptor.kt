package eu.kanade.tachiyomi.extension.ar.mangatek

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import keiyoushi.utils.tryParse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import kotlin.text.Charsets

class SpeechBubblePainterInterceptor(
    private val fontSizeProvider: () -> Int,
    @Suppress("unused") private val httpClient: OkHttpClient,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val urlString = request.url.toString()

        // 1. Intercept only requests carrying our JSON bubble array payload
        if (!MangaTek.PAGE_REGEX.matches(urlString)) {
            return chain.proceed(request)
        }

        val rawJsonFragment = urlString.substringAfterLast("#")

        // Use safe standard Charsets to decode without hardcoded string parsing
        val decodedJson = try {
            URLDecoder.decode(rawJsonFragment, Charsets.UTF_8.name())
        } catch (e: Exception) {
            rawJsonFragment.replace("%23", "#")
        }

        val bubbles = decodedJson.tryParse<List<Bubble>>() ?: return chain.proceed(request)

        // 2. Load the actual original clean image without the fragment metadata
        val cleanRequest = request.newBuilder()
            .url(urlString.substringBefore("#"))
            .build()

        val originalResponse = chain.proceed(cleanRequest)
        if (!originalResponse.isSuccessful) return originalResponse

        val imageBytes = originalResponse.body?.bytes() ?: return originalResponse
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return originalResponse

        // 3. Clone and mutate the bitmap
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap.recycle()

        val canvas = Canvas(mutableBitmap)

        // Define painting materials
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSizeProvider().toFloat()
            textAlign = Paint.Align.CENTER
        }

        val bubbleBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val width = mutableBitmap.width.toFloat()
        val height = mutableBitmap.height.toFloat()

        // Reuse a single RectF instance to prevent GC thrashing inside the loop
        val drawRect = RectF()

        // 4. Paint each bubble dynamically based on styling coordinates
        for (bubble in bubbles) {
            val left = (bubble.left / 100f) * width
            val top = (bubble.top / 100f) * height
            val rectWidth = (bubble.width / 100f) * width
            val rectHeight = (bubble.height / 100f) * height

            canvas.save()

            // Translate origin to the center of our targeted bubble coordinates
            canvas.translate(left + rectWidth / 2, top + rectHeight / 2)
            canvas.rotate(bubble.angle)

            // Update the reusable RectF coordinates
            drawRect.set(-rectWidth / 2, -rectHeight / 2, rectWidth / 2, rectHeight / 2)

            // Draw white background oval and its border
            canvas.drawOval(drawRect, bubbleBackgroundPaint)
            canvas.drawOval(drawRect, bubbleBorderPaint)

            // Calculate bounding width limits for the text inside the oval
            val textWidthLimit = (rectWidth * 0.82f).toInt().coerceAtLeast(1)

            // Compatibility-safe StaticLayout creation
            val textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(bubble.text, 0, bubble.text.length, textPaint, textWidthLimit)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(
                    bubble.text,
                    textPaint,
                    textWidthLimit,
                    Layout.Alignment.ALIGN_CENTER,
                    1.0f,
                    0f,
                    false,
                )
            }

            // Vertically center the block of text within the oval bounding box
            canvas.translate(0f, -textLayout.height / 2f)
            textLayout.draw(canvas)

            canvas.restore()
        }

        // 5. Compress the finalized image back to ByteArray using .use for stream safety
        val finalBytes = ByteArrayOutputStream().use { outputStream ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.toByteArray()
        }
        mutableBitmap.recycle()

        return originalResponse.newBuilder()
            .body(finalBytes.toResponseBody("image/jpeg".toMediaTypeOrNull()))
            .build()
    }
}
