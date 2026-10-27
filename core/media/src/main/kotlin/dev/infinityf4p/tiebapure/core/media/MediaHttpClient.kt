package dev.infinityf4p.tiebapure.core.media

import okhttp3.OkHttpClient

// Derived clients retain the same connection pool and dispatcher; request policies stay in the downloader.
internal val defaultMediaHttpClient by lazy { OkHttpClient() }
