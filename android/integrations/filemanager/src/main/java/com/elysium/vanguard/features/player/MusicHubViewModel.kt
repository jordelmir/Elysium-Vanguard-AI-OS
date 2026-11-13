package com.elysium.vanguard.features.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.media.audiofx.DynamicsProcessing
import androidx.media3.session.MediaSession
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import android.os.Bundle
import android.os.Build
import android.annotation.SuppressLint
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.elysium.vanguard.core.util.SafeLog as Log
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

@HiltViewModel
@SuppressLint("UnsafeOptInUsageError")
class MusicHubViewModel @Inject constructor(
    private val repository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("elysium_music_prefs", Context.MODE_PRIVATE)

    /** Durable, corruption tolerant local storage for the client playlists. */
    private val playlistStore = PlaylistStore(SharedPreferencesPlaylistStorage(prefs))

    companion object {
        /** Maximum length of a playlist name. */
        const val MAX_PLAYLIST_NAME = 48
    }

    val exoPlayer = ExoPlayer.Builder(context).build()

    private val _songs = MutableStateFlow<List<MusicTrack>>(emptyList())
    val songs: StateFlow<List<MusicTrack>> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /**
     * Non null when the local store had to recover something on load. The
     * client is told instead of silently losing data.
     */
    private val _playlistStoreNotice = MutableStateFlow<String?>(null)
    val playlistStoreNotice: StateFlow<String?> = _playlistStoreNotice.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _boostLevel = MutableStateFlow(1.0f) // 1.0 to 10.0 (Supreme Boost)
    val boostLevel: StateFlow<Float> = _boostLevel.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val activeQueue: StateFlow<List<MusicTrack>> = _activeQueue.asStateFlow()

    private val _recents = MutableStateFlow<List<MusicTrack>>(emptyList())
    val recents: StateFlow<List<MusicTrack>> = _recents.asStateFlow()

    private val _knownSongs = MutableStateFlow<Set<Long>>(emptySet())

    private var mediaSession: MediaSession? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.d("MusicHubViewModel", "MediaStore change detected, refreshing library...")
            loadLibrary()
        }
    }

    init {
        loadFavorites()
        loadKnownSongs()
        loadRecents()
        loadPlaylists()
        loadLibrary(isInitial = true)
        setupExoPlayer()
        setupMediaSession()

        // PRO SCANNER: Register observer for real-time updates
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
    }

    private fun setupMediaSession() {
        val stopCommand = SessionCommand("ACTION_STOP", Bundle.EMPTY)
        val stopButton = CommandButton.Builder()
            .setDisplayName("Stop")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(stopCommand)
            .build()

        mediaSession = MediaSession.Builder(context, exoPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_STOP") {
                        stopPlayback()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            })
            .setCustomLayout(listOf(stopButton))
            .build()
    }

    private fun setupExoPlayer() {
        exoPlayer.volume = 1.0f
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    applyBoostEffect()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val path = mediaItem?.localConfiguration?.uri?.path ?: return
                _currentTrack.value = _songs.value.find { it.path == path }
                applyBoostEffect()
            }
            override fun onPlaybackStateChanged(state: Int) {
                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_READY) {
                    applyBoostEffect()
                }
                if (state == Player.STATE_ENDED) {
                    skipNext()
                }
            }
        })

        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _currentPosition.value = exoPlayer.currentPosition
                    if (_duration.value > 0) {
                        _progress.value = _currentPosition.value.toFloat() / _duration.value
                    }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        context.contentResolver.unregisterContentObserver(mediaStoreObserver)
        dynamicsProcessing?.release()
        mediaSession?.release()
        exoPlayer.release()
    }

    fun playTrack(track: MusicTrack, customQueue: List<MusicTrack>? = null) {
        _currentTrack.value = track
        addToRecents(track)

        // If a custom queue is provided (Favorites, Playlist), use it.
        // Otherwise, fallback to the full library.
        _activeQueue.value = customQueue ?: _songs.value

        val mediaItem = MediaItem.fromUri(track.path)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun stopPlayback() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentTrack.value = null
        _activeQueue.value = emptyList()
        _isPlaying.value = false
    }

    fun skipNext() {
        val currentList = _activeQueue.value.ifEmpty { _songs.value }
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.id == _currentTrack.value?.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playTrack(currentList[currentIndex + 1], currentList)
        } else {
            playTrack(currentList[0], currentList)
        }
    }

    fun skipPrevious() {
        val currentList = _activeQueue.value.ifEmpty { _songs.value }
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.id == _currentTrack.value?.id }
        if (currentIndex != -1 && currentIndex > 0) {
            playTrack(currentList[currentIndex - 1], currentList)
        } else {
            playTrack(currentList.last(), currentList)
        }
    }

    fun seekTo(progress: Float) {
        val targetPos = (progress * _duration.value).toLong()
        exoPlayer.seekTo(targetPos)
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        exoPlayer.volume = vol
    }

    fun setBoost(level: Float) {
        _boostLevel.value = level
        applyBoostEffect()
    }

    /**
     * PHASE 0.4: DynamicsProcessing is API 28+. We guard with
     * [Build.VERSION.SDK_INT] and silence the NewApi lint check.
     */
    @SuppressLint("NewApi")
    private fun applyBoostEffect() {
        // On API 26-27 we silently skip the bass engine — playback still works.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d("SupremeBass", "Skipping DynamicsProcessing on API ${Build.VERSION.SDK_INT} (requires API 28+)")
            return
        }
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == -1) return

        try {
            if (dynamicsProcessing == null || dynamicsProcessing?.id != sessionId) {
                dynamicsProcessing?.release()

                // SUPREME BASS ENGINE: Limiter + MBC
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1,     // Channel count
                    true,  // Pre-EQ (Used for Bass focus)
                    4,     // Pre-EQ bands
                    true,  // Multi-band compressor
                    4,     // MBC bands
                    false, // Post-EQ
                    0,     // Post-EQ bands
                    true   // Limiter
                ).build()

                dynamicsProcessing = DynamicsProcessing(0, sessionId, config)
            }

            dynamicsProcessing?.let { dp ->
                val boost = _boostLevel.value
                val gainDb = boostToDb(boost)

                // 1. INPUT GAIN (BASS BOOST) - Target frequencies below 200Hz
                val preEq = dp.getPreEqByChannelIndex(0)
                val bassBand = preEq.getBand(0)
                bassBand.isEnabled = true
                bassBand.cutoffFrequency = 200f
                // Aggressive bass: up to 18dB for the sub-bass band
                bassBand.gain = (gainDb * 1.8f).coerceAtMost(18f)
                preEq.setBand(0, bassBand)
                // Mid-bass emphasis (200-500Hz) for fullness
                if (preEq.bandCount > 1) {
                    val midBassBand = preEq.getBand(1)
                    midBassBand.isEnabled = true
                    midBassBand.cutoffFrequency = 500f
                    midBassBand.gain = (gainDb * 0.8f).coerceAtMost(10f)
                    preEq.setBand(1, midBassBand)
                }
                dp.setPreEqByChannelIndex(0, preEq)

                // 2. MULTI-BAND COMPRESSION (TONE CONTROL)
                val mbc = dp.getMbcByChannelIndex(0)
                for (i in 0 until 4) {
                    val band = mbc.getBand(i)
                    band.isEnabled = true
                    band.attackTime = 3f   // Faster attack for punchier bass
                    band.releaseTime = 30f
                    band.ratio = 3f        // Harder compression for loudness
                    band.threshold = -15f  // Lower threshold catches more signal
                    band.kneeWidth = 0f
                    band.noiseGateThreshold = -60f
                    band.expanderRatio = 1f
                    // Bass bands get more pre-gain, highs less
                    band.preGain = when (i) {
                        0 -> (gainDb * 1.2f).coerceAtMost(20f)  // Sub-bass
                        1 -> (gainDb * 0.8f).coerceAtMost(14f)  // Mid-bass
                        2 -> (gainDb * 0.3f).coerceAtMost(8f)   // Mids
                        else -> (gainDb * 0.1f).coerceAtMost(4f) // Highs
                    }
                    band.postGain = (gainDb * 0.5f).coerceAtMost(12f) // More output
                    mbc.setBand(i, band)
                }
                dp.setMbcByChannelIndex(0, mbc)

                // 3. MASTER LIMITER (VOLUME BOOST)
                val limiter = dp.getLimiterByChannelIndex(0)
                limiter.isEnabled = true
                limiter.ratio = 8f  // Slightly less limiting to let peaks through
                limiter.postGain = (gainDb * 1.3f).coerceAtMost(30f) // Aggressive output
                dp.setLimiterByChannelIndex(0, limiter)

                dp.setEnabled(boost > 1.0f)
                Log.d("SupremeBass", "Applied Supreme Boost: ${boost}x (${gainDb}dB + Bass Focus)")
            }
        } catch (e: Exception) {
            Log.e("SupremeBass", "Error applying Elite Audio processing", e)
        }
    }

    private fun boostToDb(boost: Float): Float {
        // Aggressive exponential curve: each step feels dramatically louder
        // 1x = 0dB, 2x = 6dB, 3x = 12dB, 5x = 20dB, 8x = 27dB, 10x = 30dB
        if (boost <= 1.0f) return 0f
        // Use log2-based curve: perceived loudness doubles every ~6dB
        val normalized = (boost - 1.0f) / 9.0f // 0..1 range
        // Cubic curve for aggressive perceived loudness increase at every step
        return (normalized * 30f).coerceAtMost(30f)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        exoPlayer.shuffleModeEnabled = _isShuffle.value
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = nextMode
    }

    fun refreshLibrary() {
        loadLibrary(isInitial = false)
    }

    private fun loadLibrary(isInitial: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getMusicFiles().collect { allTracks ->
                val currentKnown = _knownSongs.value.toMutableSet()
                val newlyDiscovered = mutableListOf<MusicTrack>()

                allTracks.forEach { track ->
                    if (!currentKnown.contains(track.id)) {
                        currentKnown.add(track.id)
                        if (!isInitial) {
                            newlyDiscovered.add(track)
                        }
                    }
                }

                // Update known songs immediately
                if (currentKnown.size != _knownSongs.value.size) {
                    _knownSongs.value = currentKnown
                    saveKnownSongs(currentKnown)
                }

                // If new tracks were found during background scan/manual sync, push to recents
                if (newlyDiscovered.isNotEmpty()) {
                    Log.d("MusicHubViewModel", "🚨 DISCOVERY: ${newlyDiscovered.size} new tracks found!")
                    val updatedRecents = _recents.value.toMutableList()
                    // Prepend new tracks to recents (newest first)
                    newlyDiscovered.reversed().forEach { track ->
                        updatedRecents.removeAll { it.id == track.id }
                        updatedRecents.add(0, track.copy(isFavorite = _favorites.value.contains(track.id)))
                    }
                    val limitedRecents = updatedRecents.take(20)
                    _recents.value = limitedRecents
                    saveRecents(limitedRecents)
                } else if (isInitial && _recents.value.isEmpty() && allTracks.isNotEmpty()) {
                    // PRO INITIALIZATION: If recents is empty on first run, populate with newest songs
                    Log.d("MusicHubViewModel", "Initializing Recents with newest songs from device")
                    val initialRecents = allTracks.take(10).map {
                        it.copy(isFavorite = _favorites.value.contains(it.id))
                    }
                    _recents.value = initialRecents
                    saveRecents(initialRecents)
                }

                _songs.value = allTracks.map { track ->
                    track.copy(isFavorite = _favorites.value.contains(track.id))
                }
                _isLoading.value = false
            }
        }
    }

    private fun loadKnownSongs() {
        val knownJson = prefs.getString("known_songs", "[]")
        val type = object : TypeToken<Set<Long>>() {}.type
        val knownSet: Set<Long> = gson.fromJson(knownJson, type) ?: emptySet()
        _knownSongs.value = knownSet
    }

    private fun saveKnownSongs(set: Set<Long>) {
        prefs.edit().putString("known_songs", gson.toJson(set)).apply()
    }

    private fun loadFavorites() {
        val favJson = prefs.getString("favorites", "[]")
        val type = object : TypeToken<Set<Long>>() {}.type
        val favSet: Set<Long> = gson.fromJson(favJson, type) ?: emptySet()
        _favorites.value = favSet
    }

    fun toggleFavorite(trackId: Long) {
        val currentFavs = _favorites.value.toMutableSet()
        if (currentFavs.contains(trackId)) {
            currentFavs.remove(trackId)
        } else {
            currentFavs.add(trackId)
        }
        _favorites.value = currentFavs
        prefs.edit().putString("favorites", gson.toJson(currentFavs)).apply()

        // Update current songs list
        _songs.value = _songs.value.map {
            if (it.id == trackId) it.copy(isFavorite = currentFavs.contains(trackId)) else it
        }

        // Update recents list if the track is there
        _recents.value = _recents.value.map {
            if (it.id == trackId) it.copy(isFavorite = currentFavs.contains(trackId)) else it
        }
    }

    // RECENTS MANAGEMENT
    private fun loadRecents() {
        val recentsJson = prefs.getString("recents", "[]")
        val type = object : TypeToken<List<MusicTrack>>() {}.type
        val recentsList: List<MusicTrack> = gson.fromJson(recentsJson, type) ?: emptyList()
        _recents.value = recentsList
    }

    private fun addToRecents(track: MusicTrack, persist: Boolean = true) {
        val currentRecents = _recents.value.toMutableList()
        // Remove if exists to move to top
        currentRecents.removeAll { it.id == track.id }

        // Add to top
        currentRecents.add(0, track.copy(isFavorite = _favorites.value.contains(track.id)))

        // Limit to 20
        val limitedRecents = currentRecents.take(20)
        _recents.value = limitedRecents

        if (persist) {
            saveRecents(limitedRecents)
        }
    }

    private fun saveRecents(list: List<MusicTrack>) {
        prefs.edit().putString("recents", gson.toJson(list)).apply()
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAYLIST MANAGEMENT
    //
    // Playlists live in the local PlaylistStore: one key per playlist, with a
    // backup of the previous good copy and a transparent migration of the
    // legacy single-blob format. A playlist is only removed when the client
    // explicitly deletes it.
    // ══════════════════════════════════════════════════════════════════

    private fun loadPlaylists() {
        val result = playlistStore.load()
        _playlists.value = result.playlists
        _playlistStoreNotice.value = when {
            result.lost > 0 ->
                "RECOVERED ${result.recovered} LISTA(S) · ${result.lost} NO SE PUDO LEER (COPIA RESERVADA)"
            result.recovered > 0 ->
                "SE RECUPERARON ${result.recovered} LISTA(S) DESDE LA COPIA DE SEGURIDAD"
            result.migrated > 0 ->
                "MIGRADAS ${result.migrated} LISTA(S) AL ALMACENAMIENTO LOCAL"
            else -> null
        }
    }

    /**
     * Validates a playlist name. Returns null when the name is acceptable.
     */
    fun playlistNameError(name: String, exceptId: Long? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "EL NOMBRE ES OBLIGATORIO"
        if (trimmed.length > MAX_PLAYLIST_NAME) return "MAXIMO $MAX_PLAYLIST_NAME CARACTERES"
        if (_playlists.value.any { it.id != exceptId && it.name.equals(trimmed, ignoreCase = true) }) {
            return "YA EXISTE UNA LISTA CON ESE NOMBRE"
        }
        return null
    }

    /**
     * Creates a playlist and returns its id, or null when the name was not
     * accepted. The playlist is stored locally right away.
     */
    fun createPlaylist(name: String): Long? {
        val trimmed = name.trim()
        if (playlistNameError(trimmed) != null) return null
        val id = System.currentTimeMillis()
        val newList = _playlists.value + Playlist(
            id = id,
            name = trimmed,
            tracks = emptyList()
        )
        savePlaylists(newList)
        return id
    }

    fun renamePlaylist(id: Long, newName: String): Boolean {
        val trimmed = newName.trim()
        if (playlistNameError(trimmed, exceptId = id) != null) return false
        val newList = _playlists.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        savePlaylists(newList)
        return true
    }

    fun deletePlaylist(id: Long) {
        val newList = _playlists.value.filter { it.id != id }
        savePlaylists(newList)
        playlistStore.delete(id)
    }

    fun addTrackToPlaylist(playlistId: Long, track: MusicTrack) {
        val newList = _playlists.value.map {
            if (it.id == playlistId) {
                if (!it.tracks.any { t -> t.id == track.id }) {
                    it.copy(tracks = it.tracks + track)
                } else it
            } else it
        }
        savePlaylists(newList)
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val newList = _playlists.value.map {
            if (it.id == playlistId) {
                it.copy(tracks = it.tracks.filter { t -> t.id != trackId })
            } else it
        }
        savePlaylists(newList)
    }

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        // Durable write: every playlist lands in its own key, and the previous
        // good copy is kept so a corrupted write can be rolled back.
        list.forEach { playlistStore.save(it) }

        // Update selection if it was deleted or changed
        val currentSelectedId = _selectedPlaylist.value?.id
        if (currentSelectedId != null) {
            _selectedPlaylist.value = list.find { it.id == currentSelectedId }
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }
}
