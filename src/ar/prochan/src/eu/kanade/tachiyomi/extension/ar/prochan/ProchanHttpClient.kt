package eu.kanade.tachiyomi.extension.ar.prochan

import android.app.Application
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ProchanHttpClient(context: Context) {
    
    private val application: Application = context.applicationContext as Application
    
    fun getClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(ProchanConstants.DEFAULT_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(ProchanConstants.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(ProchanConstants.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(ProchanInterceptor())
            .addNetworkInterceptor(CacheInterceptor())
            .addInterceptor(RetryInterceptor())

        // أضف مسجل HTTP للتطوير
        if (BuildConfig.DEBUG) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(httpLoggingInterceptor)
        }

        return builder.build()
    }
}
