package eu.kanade.tachiyomi.extension.ar.prochan

import android.content.Context
import android.preference.PreferenceManager

class ProchanPreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // ==================== Image Quality ====================
    fun getImageQuality(): String {
        return prefs.getString("image_quality", "high") ?: "high"
    }

    fun setImageQuality(quality: String) {
        prefs.edit().putString("image_quality", quality).apply()
    }

    // ==================== Reading History ====================
    fun isSaveHistoryEnabled(): Boolean {
        return prefs.getBoolean("save_history", true)
    }

    fun setSaveHistory(enabled: Boolean) {
        prefs.edit().putBoolean("save_history", enabled).apply()
    }

    // ==================== Language ====================
    fun getLanguage(): String {
        return prefs.getString("language", "ar") ?: "ar"
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
    }

    // ==================== Theme ====================
    fun getTheme(): String {
        return prefs.getString("theme", "dark") ?: "dark"
    }

    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
    }

    // ==================== Cache ====================
    fun shouldCacheImages(): Boolean {
        return prefs.getBoolean("cache_images", true)
    }

    fun setCacheImages(enabled: Boolean) {
        prefs.edit().putBoolean("cache_images", enabled).apply()
    }

    fun getCacheSizeLimit(): Long {
        return prefs.getLong("cache_size_limit", ProchanConstants.CACHE_SIZE)
    }

    fun setCacheSizeLimit(size: Long) {
        prefs.edit().putLong("cache_size_limit", size).apply()
    }

    // ==================== Reader Settings ====================
    fun getReaderMode(): String {
        return prefs.getString("reader_mode", "vertical") ?: "vertical"
    }

    fun setReaderMode(mode: String) {
        prefs.edit().putString("reader_mode", mode).apply()
    }

    fun isWebtoonMode(): Boolean {
        return getReaderMode() == "webtoon"
    }

    // ==================== Notifications ====================
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun notifyOnNewChapter(): Boolean {
        return prefs.getBoolean("notify_new_chapter", true)
    }

    fun setNotifyOnNewChapter(enabled: Boolean) {
        prefs.edit().putBoolean("notify_new_chapter", enabled).apply()
    }

    // ==================== Data ====================
    fun clearAllPreferences() {
        prefs.edit().clear().apply()
    }

    fun resetToDefaults() {
        clearAllPreferences()
        setImageQuality("high")
        setSaveHistory(true)
        setLanguage("ar")
        setTheme("dark")
        setCacheImages(true)
        setReaderMode("vertical")
        setNotificationsEnabled(true)
        setNotifyOnNewChapter(true)
    }
}
