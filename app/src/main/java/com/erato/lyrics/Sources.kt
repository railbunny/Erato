package com.erato.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class LyricResult(
    val track: String,
    val artist: String,
    val album: String,
    val duration: Double,
    val synced: String?,
    val plain: String?,
    val source: String
)

// Every source implements this. Adding a source = adding one new object.
interface LyricsSource {
    val name: String
    val needsKey: Boolean get() = false
    fun search(query: String): List<LyricResult>
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpsURLConnection
    c.setRequestProperty("User-Agent", "LyricsApp/0.1")
    c.connectTimeout = 15000
    c.readTimeout = 15000
    return c.inputStream.bufferedReader().use { it.readText() }
}

object LrcLib : LyricsSource {
    override val name = "LRCLIB"
    override fun search(query: String): List<LyricResult> {
        val text = httpGet("https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8"))
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LyricResult(
                track = o.str("trackName") ?: "Unknown",
                artist = o.str("artistName") ?: "Unknown",
                album = o.str("albumName") ?: "",
                duration = o.optDouble("duration", 0.0),
                synced = o.str("syncedLyrics"),
                plain = o.str("plainLyrics"),
                source = name
            )
        }
    }
}

val allSources: List<LyricsSource> = listOf(LrcLib)
