import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Library of Ohara"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es", "it", "ar", "fr").forEach {
        source {
            lang = it
            baseUrl = "https://procomic.net/ar"
        }
    }
}
