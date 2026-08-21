package eu.kanade.tachiyomi.extension.ar.prochan

import android.util.Log

object ProchanLogger {
    private const val TAG = "Prochan"
    private var debugMode = false

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun d(message: String, throwable: Throwable? = null) {
        if (debugMode) {
            if (throwable != null) {
                Log.d(TAG, message, throwable)
            } else {
                Log.d(TAG, message)
            }
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, message, throwable)
        } else {
            Log.w(TAG, message)
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun v(message: String) {
        if (debugMode) {
            Log.v(TAG, message)
        }
    }

    // ==================== Structured Logging ====================
    fun logApiCall(url: String, method: String = "GET") {
        d("API Call: $method $url")
    }

    fun logResponse(statusCode: Int, url: String) {
        d("Response: $statusCode from $url")
    }

    fun logError(error: String, url: String? = null) {
        val message = if (url != null) {
            "Error from $url: $error"
        } else {
            "Error: $error"
        }
        e(message)
    }

    fun logParsing(dataType: String, count: Int) {
        d("Parsed $count $dataType")
    }
}
