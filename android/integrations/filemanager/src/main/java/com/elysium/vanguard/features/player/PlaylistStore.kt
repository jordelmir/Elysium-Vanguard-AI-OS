package com.elysium.vanguard.features.player

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * [PlaylistStorage] backed by the app SharedPreferences.
 */
class SharedPreferencesPlaylistStorage(
    private val prefs: SharedPreferences
) : PlaylistStorage {

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(entries: List<Pair<String, String?>>) {
        if (entries.isEmpty()) return
        val editor = prefs.edit()
        for ((key, value) in entries) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.apply()
    }

    override fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    override fun keys(): Set<String> = prefs.all.keys
}

/**
 * A user created playlist. Stored locally, never removed unless the client asks for it.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val tracks: List<MusicTrack>
)

/**
 * Minimal key/value abstraction over the device storage.
 */
interface PlaylistStorage {
    fun get(key: String): String?
    fun put(entries: List<Pair<String, String?>>)
    fun remove(keys: Collection<String>)
    fun keys(): Set<String>
}

/**
 * PLAYLIST STORE
 *
 * Local, durable storage for the playlists the client creates.
 *
 * DESIGN CONTRACT (the whole point of this class):
 *
 *  1. A playlist is only ever removed when the CLIENT explicitly deletes it.
 *  2. A single corrupted entry can NEVER take the rest of the collection down.
 *     Each playlist lives in its own key, so a bad write is isolated to one
 *     playlist instead of poisoning the whole blob (which is exactly how the
 *     previous single-blob implementation used to lose every playlist).
 *  3. Every write keeps the previous good copy, so a corrupted write can be
 *     recovered automatically.
 *  4. A legacy single-blob installation is migrated transparently, so nobody
 *     loses the playlists they already had.
 *
 * The storage is abstracted so the whole thing can be unit tested on the JVM
 * without an Android device.
 */
class PlaylistStore(
    private val storage: PlaylistStorage,
    private val gson: Gson = Gson()
) {

    companion object {
        /** Ordered list of playlist ids. */
        const val KEY_INDEX = "playlist_index"

        /** Pre-4.31 single blob format. Read once and migrated. */
        const val KEY_LEGACY = "playlists"

        private const val PREFIX = "playlist_"
        private const val SUFFIX_BACKUP = "_bak"
        private const val SUFFIX_CORRUPT = "_corrupt"

        private fun keyOf(id: Long) = "$PREFIX$id"
        private fun backupKeyOf(id: Long) = "$PREFIX$id$SUFFIX_BACKUP"
        private fun corruptKeyOf(id: Long) = "$PREFIX$id$SUFFIX_CORRUPT"
    }

    /**
     * Outcome of a load. [lost] counts playlists that were unreadable AND had
     * no recoverable copy — their raw bytes are kept aside instead of being
     * thrown away, and the client is told about it.
     */
    data class LoadResult(
        val playlists: List<Playlist> = emptyList(),
        val migrated: Int = 0,
        val recovered: Int = 0,
        val lost: Int = 0
    )

    private val listType = object : TypeToken<List<Playlist>>() {}.type

    // ── LOAD ──────────────────────────────────────────────────────────────

    fun load(): LoadResult {
        val index = readIndex()

        // Legacy installation: one single blob with every playlist.
        if (index == null) {
            val legacyRaw = storage.get(KEY_LEGACY)
            if (legacyRaw.isNullOrBlank()) {
                // Nothing stored yet. Adopt any orphan keys (index lost, data alive).
                val orphans = adoptOrphans()
                return LoadResult(playlists = orphans)
            }
            return migrateLegacy(legacyRaw)
        }

        val playlists = mutableListOf<Playlist>()
        var recovered = 0
        var lost = 0

        for (id in index) {
            when (val read = readPlaylist(id)) {
                is PlaylistRead.Ok -> playlists += read.playlist
                is PlaylistRead.Recovered -> {
                    playlists += read.playlist
                    recovered++
                }
                is PlaylistRead.Lost -> {
                    lost++
                }
            }
        }

        // Playlists on disk that the index does not know about: the index write
        // was interrupted. Adopt them instead of ignoring them.
        val known = index.toSet()
        val orphans = storage.keys()
            .filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX_BACKUP).not() && it.endsWith(SUFFIX_CORRUPT).not() }
            .map { it.removePrefix(PREFIX) }
            .mapNotNull { it.toLongOrNull() }
            .filter { it !in known }

        if (orphans.isNotEmpty()) {
            for (id in orphans) {
                when (val read = readPlaylist(id)) {
                    is PlaylistRead.Ok -> playlists += read.playlist
                    is PlaylistRead.Recovered -> {
                        playlists += read.playlist
                        recovered++
                    }
                    is PlaylistRead.Lost -> lost++
                }
            }
            writeIndex((playlists.map { it.id }))
        }

        return LoadResult(playlists = playlists, recovered = recovered, lost = lost)
    }

    private sealed class PlaylistRead {
        data class Ok(val playlist: Playlist) : PlaylistRead()
        data class Recovered(val playlist: Playlist) : PlaylistRead()
        data class Lost(val id: Long) : PlaylistRead()
    }

    private fun readPlaylist(id: Long): PlaylistRead {
        val primary = storage.get(keyOf(id))
        if (primary != null) {
            val parsed = parsePlaylist(primary)
            if (parsed != null) return PlaylistRead.Ok(parsed)
        }
        // Primary is missing or corrupt: fall back to the last good copy.
        val backup = storage.get(backupKeyOf(id))
        if (backup != null) {
            val parsed = parsePlaylist(backup)
            if (parsed != null) return PlaylistRead.Recovered(parsed)
        }
        // Nothing readable. Keep the raw bytes aside instead of losing them.
        val raw = primary ?: backup
        if (raw != null) {
            storage.put(listOf(corruptKeyOf(id) to raw))
        }
        return PlaylistRead.Lost(id)
    }

    private fun parsePlaylist(raw: String): Playlist? = try {
        gson.fromJson(raw, Playlist::class.java)
    } catch (e: JsonSyntaxException) {
        null
    } catch (e: Exception) {
        null
    }

    private fun readIndex(): List<Long>? {
        val raw = storage.get(KEY_INDEX) ?: return null
        return try {
            val ids: List<Long?> = gson.fromJson(
                raw,
                object : TypeToken<List<Long?>>() {}.type
            )
            ids.filterNotNull().distinct()
        } catch (e: Exception) {
            null
        }
    }

    private fun writeIndex(ids: List<Long>) {
        storage.put(listOf(KEY_INDEX to gson.toJson(ids)))
    }

    /**
     * Migrates the old single-blob format to one key per playlist. The legacy
     * blob is only removed once every playlist has been written successfully.
     */
    private fun migrateLegacy(raw: String): LoadResult {
        val parsed = try {
            gson.fromJson(raw, object : TypeToken<List<Playlist>>() {})
        } catch (e: Exception) {
            null
        }
        if (parsed == null) {
            // Unreadable: keep the bytes for manual recovery and report it.
            storage.put(listOf("${KEY_LEGACY}$SUFFIX_CORRUPT" to raw))
            storage.remove(listOf(KEY_LEGACY))
            return LoadResult(lost = 1)
        }
        if (parsed.isEmpty()) {
            storage.remove(listOf(KEY_LEGACY))
            return LoadResult()
        }
        val entries = parsed.map { keyOf(it.id) to gson.toJson(it) }
        storage.put(entries)
        writeIndex(parsed.map { it.id })
        storage.remove(listOf(KEY_LEGACY))
        return LoadResult(playlists = parsed, migrated = parsed.size)
    }

    private fun adoptOrphans(): List<Playlist> {
        val ids = storage.keys()
            .filter { it.startsWith(PREFIX) && !it.endsWith(SUFFIX_BACKUP) && !it.endsWith(SUFFIX_CORRUPT) }
            .map { it.removePrefix(PREFIX) }
            .mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return emptyList()
        val playlists = mutableListOf<Playlist>()
        for (id in ids) {
            when (val read = readPlaylist(id)) {
                is PlaylistRead.Ok -> playlists += read.playlist
                is PlaylistRead.Recovered -> playlists += read.playlist
                is PlaylistRead.Lost -> Unit
            }
        }
        if (playlists.isNotEmpty()) writeIndex(playlists.map { it.id })
        return playlists
    }

    // ── WRITE ─────────────────────────────────────────────────────────────

    /**
     * Creates or updates one playlist. The previous content is kept as a
     * backup so a corrupted write can be rolled back on the next load.
     */
    fun save(playlist: Playlist) {
        val json = gson.toJson(playlist)
        val previous = storage.get(keyOf(playlist.id))
        val entries = mutableListOf<Pair<String, String?>>()
        entries += keyOf(playlist.id) to json
        if (previous != null && previous != json) {
            entries += backupKeyOf(playlist.id) to previous
        }
        storage.put(entries)

        val index = readIndex() ?: emptyList()
        if (playlist.id !in index) {
            writeIndex(index + playlist.id)
        }
    }

    /**
     * Removes a playlist. This is the ONLY way a playlist ever disappears:
     * the client has to ask for it.
     */
    fun delete(id: Long) {
        storage.remove(listOf(keyOf(id), backupKeyOf(id)))
        val index = readIndex() ?: return
        writeIndex(index.filter { it != id })
    }

    /**
     * Replaces the whole collection. Used by the batch operations.
     */
    fun saveAll(playlists: List<Playlist>) {
        val entries = playlists.map { keyOf(it.id) to gson.toJson(it) }
        storage.put(entries)
        writeIndex(playlists.map { it.id })
    }
}
