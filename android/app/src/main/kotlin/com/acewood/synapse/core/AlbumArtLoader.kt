package com.acewood.synapse.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

/** Loads HA entity_picture URLs with the node token in a header, never in the URL. */
object AlbumArtLoader {
    private val client = OkHttpClient()
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(view: ImageView, picture: String?) {
        val cfg = HaRepository.config
        val token = cfg?.haToken
        if (picture.isNullOrBlank() || cfg == null || token.isNullOrBlank()) {
            view.setImageDrawable(null); return
        }
        val url = if (picture.startsWith("http://") || picture.startsWith("https://")) picture
            else cfg.haUrl.trimEnd('/') + "/" + picture.trimStart('/')
        view.tag = url
        synchronized(cache) { cache.get(url) }?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        executor.execute {
            val bitmap = runCatching {
                // Only send the HA token to Home Assistant itself. External art URLs (CDNs) get no auth header.
                val toHa = url.startsWith(cfg.haUrl.trimEnd('/') + "/")
                val request = Request.Builder().url(url).apply { if (toHa) header("Authorization", "Bearer $token") }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                }
            }.getOrNull() ?: return@execute
            synchronized(cache) { cache.put(url, bitmap) }
            view.post { if (view.tag == url) view.setImageBitmap(bitmap) }
        }
    }
}
