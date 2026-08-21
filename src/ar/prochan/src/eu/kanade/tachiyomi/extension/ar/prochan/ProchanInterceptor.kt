package eu.kanade.tachiyomi.extension.ar.prochan

import okhttp3.Interceptor
import okhttp3.Response

class ProchanInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // إضافة Headers المخصصة
        val requestWithHeaders = originalRequest.newBuilder()
            .header("User-Agent", ProchanConstants.USER_AGENT)
            .header("Accept-Language", ProchanConstants.ACCEPT_LANGUAGE)
            .header("Accept-Encoding", ProchanConstants.ACCEPT_ENCODING)
            .header("Accept", ProchanConstants.ACCEPT)
            .header("Referer", ProchanConstants.BASE_URL_AR)
            .header("DNT", "1")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .build()

        var response = chain.proceed(requestWithHeaders)

        // معالجة إعادة التوجيه
        var retryCount = 0
        while ((response.code == 301 || response.code == 302 || response.code == 307 || response.code == 308) && 
               retryCount < ProchanConstants.MAX_RETRIES) {
            val location = response.headers["Location"] ?: break
            response.close()

            val redirectRequest = originalRequest.newBuilder()
                .url(location)
                .build()

            response = chain.proceed(redirectRequest)
            retryCount++
        }

        return response
    }
}

class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response: Response? = null
        var exception: Exception? = null

        // محاولة إعادة الطلب عند الفشل
        for (i in 0 until ProchanConstants.MAX_RETRIES) {
            try {
                response = chain.proceed(request)
                if (response.isSuccessful) {
                    return response
                }
                response.close()
            } catch (e: Exception) {
                exception = e
                if (i == ProchanConstants.MAX_RETRIES - 1) {
                    throw e
                }
            }
        }

        return response ?: throw exception ?: Exception("Failed to get response")
    }
}

class CacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalResponse = chain.proceed(originalRequest)

        // تعيين سياسة الذاكرة المؤقتة
        val cacheControl = when {
            originalRequest.url.toString().contains("/chapter/") -> {
                // احفظ صفحات الفصول لمدة أطول
                "public, max-age=604800" // أسبوع واحد
            }
            originalRequest.url.toString().contains("/series") -> {
                // احفظ قوائم المانهوات
                "public, max-age=86400" // يوم واحد
            }
            else -> {
                "public, max-age=3600" // ساعة واحدة
            }
        }

        return originalResponse.newBuilder()
            .header("Cache-Control", cacheControl)
            .build()
    }
}
