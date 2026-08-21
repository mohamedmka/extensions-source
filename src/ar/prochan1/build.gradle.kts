import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ProChan"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://procomic.pro/ar"
    }

    deeplink {
        path("/series/..*")
        path("/updates/..*")
        path("/chapter/..*")
    }
}
