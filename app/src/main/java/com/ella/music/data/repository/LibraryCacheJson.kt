package com.ella.music.data.repository

import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal fun songsToLibraryCacheJsonArray(songs: List<Song>): JSONArray {
    val array = JSONArray()
    songs.forEach { song ->
        array.put(
            JSONObject()
                .put("id", song.id)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("album", song.album)
                .put("albumId", song.albumId)
                .put("duration", song.duration)
                .put("path", song.path)
                .put("fileName", song.fileName)
                .put("fileSize", song.fileSize)
                .put("mimeType", song.mimeType)
                .put("dateAdded", song.dateAdded)
                .put("dateModified", song.dateModified)
                .put("trackNumber", song.trackNumber)
                .put("discNumber", song.discNumber)
                .put("albumArtist", song.albumArtist)
                .put("genre", song.genre)
                .put("year", song.year)
                .put("composer", song.composer)
                .put("arranger", song.arranger)
                .put("lyricist", song.lyricist)
                .put("coverUrl", song.coverUrl)
                .put("onlineSource", song.onlineSource)
                .put("onlineId", song.onlineId)
                .put("onlineLyrics", song.onlineLyrics)
                .put("onlineLyricTranslation", song.onlineLyricTranslation)
        )
    }
    return array
}

internal fun albumsToLibraryCacheJsonArray(albums: List<Album>): JSONArray {
    val array = JSONArray()
    albums.forEach { album ->
        array.put(
            JSONObject()
                .put("id", album.id)
                .put("name", album.name)
                .put("artist", album.artist)
                .put("songCount", album.songCount)
                .put("year", album.year)
                .put("artAlbumId", album.artAlbumId)
                .put("albumArtist", album.albumArtist)
        )
    }
    return array
}

/**
 * Stream the cached library straight off disk with [JsonReader] instead of reading the whole
 * file into a String and building a JSONObject tree. For ~800+ songs the tree form produced a
 * multi-MB string plus thousands of transient objects, contributing to the cold-start GC spike.
 */
internal fun readLibraryCacheSongs(file: File): List<Song> {
    if (!hasLibraryCache(file)) return emptyList()
    val songs = ArrayList<Song>()
    var foundSongsArray = false
    // AtomicFile restores the previous complete snapshot if the process was killed in the
    // middle of replacing the cache. A plain File reader would see a truncated JSON document
    // after a crash (for example from an overlay service), leaving every album view empty.
    AtomicFile(file).openRead().bufferedReader().use { reader ->
        JsonReader(reader).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() == "songs") {
                    foundSongsArray = true
                    json.beginArray()
                    while (json.hasNext()) {
                        songs.add(json.readCacheSong())
                    }
                    json.endArray()
                } else {
                    json.skipValue()
                }
            }
            json.endObject()
        }
    }
    check(foundSongsArray) { "Library cache is missing its songs array: ${file.name}" }
    return songs
}

internal fun hasLibraryCache(file: File): Boolean =
    file.exists() || File("${file.path}.bak").exists()

internal fun writeLibraryCacheAtomically(file: File, json: String) {
    val atomicFile = AtomicFile(file)
    val stream = atomicFile.startWrite()
    try {
        stream.write(json.toByteArray(Charsets.UTF_8))
        atomicFile.finishWrite(stream)
    } catch (error: Throwable) {
        atomicFile.failWrite(stream)
        throw error
    }
}

private fun JsonReader.readCacheSong(): Song {
    var id = 0L
    var title = ""
    var artist = ""
    var album = ""
    var albumId = 0L
    var duration = 0L
    var path = ""
    var fileName = ""
    var fileSize = 0L
    var mimeType = ""
    var dateAdded = 0L
    var dateModified = 0L
    var trackNumber = 0
    var discNumber = 0
    var albumArtist = ""
    var genre = ""
    var year = ""
    var composer = ""
    var arranger = ""
    var lyricist = ""
    var coverUrl = ""
    var onlineSource = ""
    var onlineId = ""
    var onlineLyrics = ""
    var onlineLyricTranslation = ""
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextLong()
            "title" -> title = nextStringOrEmpty()
            "artist" -> artist = nextStringOrEmpty()
            "album" -> album = nextStringOrEmpty()
            "albumId" -> albumId = nextLong()
            "duration" -> duration = nextLong()
            "path" -> path = nextStringOrEmpty()
            "fileName" -> fileName = nextStringOrEmpty()
            "fileSize" -> fileSize = nextLong()
            "mimeType" -> mimeType = nextStringOrEmpty()
            "dateAdded" -> dateAdded = nextLong()
            "dateModified" -> dateModified = nextLong()
            "trackNumber" -> trackNumber = nextInt()
            "discNumber" -> discNumber = nextInt()
            "albumArtist" -> albumArtist = nextStringOrEmpty()
            "genre" -> genre = nextStringOrEmpty()
            "year" -> year = nextStringOrEmpty()
            "composer" -> composer = nextStringOrEmpty()
            "arranger" -> arranger = nextStringOrEmpty()
            "lyricist" -> lyricist = nextStringOrEmpty()
            "coverUrl" -> coverUrl = nextStringOrEmpty()
            "onlineSource" -> onlineSource = nextStringOrEmpty()
            "onlineId" -> onlineId = nextStringOrEmpty()
            "onlineLyrics" -> onlineLyrics = nextStringOrEmpty()
            "onlineLyricTranslation" -> onlineLyricTranslation = nextStringOrEmpty()
            else -> skipValue()
        }
    }
    endObject()
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumArtist = albumArtist,
        genre = genre,
        year = year,
        composer = composer,
        arranger = arranger,
        lyricist = lyricist,
        coverUrl = coverUrl,
        onlineSource = onlineSource,
        onlineId = onlineId,
        onlineLyrics = onlineLyrics,
        onlineLyricTranslation = onlineLyricTranslation
    )
}

private fun JsonReader.nextStringOrEmpty(): String {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return ""
    }
    return nextString()
}

internal fun JSONArray.toLibraryCacheAlbumList(): List<Album> =
    List(length()) { index ->
        val item = getJSONObject(index)
        Album(
            id = item.getLong("id"),
            name = item.optString("name"),
            artist = item.optString("artist"),
            songCount = item.optInt("songCount"),
            year = item.optString("year", "").ifBlank { item.optInt("year").takeIf { it > 0 }?.toString() ?: "" },
            artAlbumId = item.optLong("artAlbumId", item.optLong("id")),
            albumArtist = item.optString("albumArtist")
        )
    }
