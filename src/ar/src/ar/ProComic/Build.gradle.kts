import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ProComic"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.5"

    source {
        lang = "ar"
        baseUrl = "https://procomic.pro/ar"
}
