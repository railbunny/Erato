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

interface LyricsSource {
    val name: String
    val available: Boolean
    val needsKey: Boolean get() = false
    fun search(query: String): List<LyricResult>
}

private fun JSONObject.stringOrNull(key: String): String? {
    return if (isNull(key)) null
    else optString(key).takeIf { it.isNotBlank() }
}

fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpsURLConnection
    connection.setRequestProperty("User-Agent", "Erato/0.2")
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000

    return try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw Exception("Server returned HTTP $status")
        }

        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

object LrcLib : LyricsSource {
    override val name = "LRCLIB"
    override val available = true

    override fun search(query: String): List<LyricResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = httpGet("https://lrclib.net/api/search?q=$encoded")
        val array = JSONArray(response)

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)

            LyricResult(
                track = item.stringOrNull("trackName") ?: "Unknown title",
                artist = item.stringOrNull("artistName") ?: "Unknown artist",
                album = item.stringOrNull("albumName") ?: "",
                duration = item.optDouble("duration", 0.0),
                synced = item.stringOrNull("syncedLyrics"),
                plain = item.stringOrNull("plainLyrics"),
                source = name
            )
        }
    }
}

class PlannedSource(
    override val name: String
) : LyricsSource {
    override val available = false

    override fun search(query: String): List<LyricResult> = emptyList()
}

val allSources: List<LyricsSource> = listOf(
    LrcLib,
    PlannedSource("lrc.red"),
    PlannedSource("NetEase"),
    PlannedSource("KuGou"),
    PlannedSource("BetterLyrics"),
    PlannedSource("BetterLyrics (QQ)"),
    PlannedSource("BiniLyrics"),
    PlannedSource("Unison"),
    PlannedSource("AMLL"),
    PlannedSource("Lyricify"),
    PlannedSource("SimpMusic"),
    PlannedSource("PaxSenix"),
    PlannedSource("LyricsPlus")
)
