plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Library of Ohara"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "ar").forEach {
        source {
            lang = ar
            baseUrl = "https://procomic.net/ar"
        }
    }
}
