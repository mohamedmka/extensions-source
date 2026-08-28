package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ProcomicFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Procomic()
    )
}
