package com.elysium.vanguard.features.player

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the local playlist storage.
 *
 * The rules under test are the ones the client asked for: a playlist lives
 * locally, it never disappears by itself, and a single broken entry can never
 * take the rest of the collection with it.
 */
class PlaylistStoreTest {

    /** In memory implementation of the storage, with a way to corrupt it. */
    private class FakeStorage : PlaylistStorage {
        val map = linkedMapOf<String, String>()

        override fun get(key: String): String? = map[key]
        override fun put(entries: List<Pair<String, String?>>) {
            entries.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
        override fun remove(keys: Collection<String>) { keys.forEach { map.remove(it) } }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private fun track(id: Long, name: String = "track-$id") = MusicTrack(
        id = id,
        name = name,
        path = "/music/$name.mp3",
        mimeType = "audio/mpeg",
        album = "Album",
        artist = "Artist",
        duration = 1000L * id,
        dateModified = 1_700_000_000L,
        isFavorite = false
    )

    private fun playlist(id: Long, name: String, tracks: List<MusicTrack> = emptyList()) =
        Playlist(id = id, name = name, tracks = tracks)

    @Test
    fun `a playlist survives a save and load`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "ROCK", listOf(track(10L))))

        val result = store.load()
        assertEquals(1, result.playlists.size)
        assertEquals("ROCK", result.playlists[0].name)
        assertEquals(1, result.playlists[0].tracks.size)
        assertEquals(0, result.lost)
    }

    @Test
    fun `a playlist is only removed when the client deletes it`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "ROCK"))
        store.save(playlist(2L, "JAZZ"))

        // Nothing else touches the storage.
        store.load()
        assertEquals(2, store.load().playlists.size)

        store.delete(2L)

        val after = store.load().playlists
        assertEquals(1, after.size)
        assertEquals("ROCK", after[0].name)
    }

    @Test
    fun `a corrupted playlist does not take the others down`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "ROCK"))
        store.save(playlist(2L, "JAZZ"))
        store.save(playlist(3L, "POP"))

        // The middle one gets corrupted on disk.
        storage.map["playlist_2"] = "{ this is not json"

        val result = store.load()
        val names = result.playlists.map { it.name }
        assertTrue("the healthy playlists must survive: $names", names.contains("ROCK"))
        assertTrue("the healthy playlists must survive: $names", names.contains("POP"))
        assertEquals(1, result.lost)
    }

    @Test
    fun `a corrupted write is recovered from the backup`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "ROCK"))
        // A second write keeps the previous good copy as the backup.
        store.save(playlist(1L, "ROCK FINAL"))
        assertEquals("ROCK FINAL", store.load().playlists[0].name)

        // The last write is corrupted.
        storage.map["playlist_1"] = "@@@ broken @@@"

        val result = store.load()
        assertEquals(1, result.playlists.size)
        assertEquals("ROCK", result.playlists[0].name)
        assertEquals(1, result.recovered)
        assertEquals(0, result.lost)
    }

    @Test
    fun `the legacy single blob is migrated without losing playlists`() {
        val storage = FakeStorage()
        val gson = Gson()
        storage.map["playlists"] = gson.toJson(
            listOf(
                playlist(1L, "VIEJA 1", listOf(track(5L))),
                playlist(2L, "VIEJA 2")
            )
        )

        val store = PlaylistStore(storage)
        val result = store.load()

        assertEquals(2, result.playlists.size)
        assertEquals(2, result.migrated)
        assertTrue(result.playlists.any { it.name == "VIEJA 1" })
        assertTrue(result.playlists.any { it.name == "VIEJA 2" })
        // The legacy blob is gone once everything is stored per playlist.
        assertNull(storage.map["playlists"])
        // And it keeps working from now on.
        assertEquals(2, store.load().playlists.size)
    }

    @Test
    fun `a corrupt legacy blob is kept aside instead of vanishing`() {
        val storage = FakeStorage()
        storage.map["playlists"] = "not json at all"

        val store = PlaylistStore(storage)
        val result = store.load()

        assertEquals(0, result.playlists.size)
        assertEquals(1, result.lost)
        // The raw bytes are preserved for recovery.
        assertNotNull(storage.map["playlists_corrupt"])
        assertEquals("not json at all", storage.map["playlists_corrupt"])
    }

    @Test
    fun `playlists written on disk without an index are adopted`() {
        val storage = FakeStorage()
        val gson = Gson()
        storage.map["playlist_9"] = gson.toJson(playlist(9L, "HUERFANA"))

        val store = PlaylistStore(storage)
        val result = store.load()

        assertEquals(1, result.playlists.size)
        assertEquals("HUERFANA", result.playlists[0].name)
        // The index is rebuilt so the orphan is now a first class citizen.
        assertNotNull(storage.map[PlaylistStore.KEY_INDEX])
    }

    @Test
    fun `the order of the playlists is preserved`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "A"))
        store.save(playlist(2L, "B"))
        store.save(playlist(3L, "C"))

        assertEquals(listOf("A", "B", "C"), store.load().playlists.map { it.name })
    }

    @Test
    fun `saving the same playlist twice updates it instead of duplicating it`() {
        val storage = FakeStorage()
        val store = PlaylistStore(storage)

        store.save(playlist(1L, "A", listOf(track(1L))))
        store.save(playlist(1L, "A", listOf(track(1L), track(2L))))

        val result = store.load()
        assertEquals(1, result.playlists.size)
        assertEquals(2, result.playlists[0].tracks.size)
    }
}
