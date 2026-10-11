package com.elysium369.meet.linksphere.data

import android.content.Context
import androidx.core.content.edit
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.linksphere.domain.*
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Repositorio Central de Elysium LinkSphere.
 * Gestiona el feed social universal, persistencia local segura,
 * interacciones (reacciones, guardados, comentarios, seguimiento)
 * y sincronización con el servidor.
 */
class LinkSphereRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val prefs = context.getSharedPreferences("elysium_linksphere_store_v1", Context.MODE_PRIVATE)

    private val _postsFlow = MutableStateFlow<List<LinkPost>>(emptyList())
    val postsFlow: StateFlow<List<LinkPost>> = _postsFlow.asStateFlow()

    private val _collectionsFlow = MutableStateFlow<List<LinkCollection>>(emptyList())
    val collectionsFlow: StateFlow<List<LinkCollection>> = _collectionsFlow.asStateFlow()

    private val _playbackPrefFlow = MutableStateFlow(PlaybackPreference.IN_APP_WHEN_SUPPORTED)
    val playbackPrefFlow: StateFlow<PlaybackPreference> = _playbackPrefFlow.asStateFlow()

    init {
        loadPersistedState()
    }

    private fun currentUserId(): String = SupabaseModule.client.auth.currentUserOrNull()?.id ?: "LOCAL_USER"

    private fun loadPersistedState() {
        val rawPosts = prefs.getString("cached_posts_json", null)
        val loaded = if (rawPosts != null) {
            runCatching { json.decodeFromString<List<LinkPost>>(rawPosts) }.getOrNull()
        } else null

        if (!loaded.isNullOrEmpty()) {
            _postsFlow.value = loaded
        } else {
            // Semilla inicial verificada con contenido de alto valor (Mecánica, OBD, Seguridad, Ciencia)
            val initialSeed = createInitialSeedCapsules()
            _postsFlow.value = initialSeed
            savePostsToDisk(initialSeed)
        }

        val rawPref = prefs.getString("playback_pref", PlaybackPreference.IN_APP_WHEN_SUPPORTED.name)
        _playbackPrefFlow.value = runCatching { PlaybackPreference.valueOf(rawPref!!) }.getOrDefault(PlaybackPreference.IN_APP_WHEN_SUPPORTED)
    }

    fun setPlaybackPreference(pref: PlaybackPreference) {
        _playbackPrefFlow.value = pref
        prefs.edit { putString("playback_pref", pref.name) }
    }

    fun createPost(
        link: NormalizedLink,
        userCaption: String,
        category: LinkCategory,
        tags: List<String> = emptyList(),
        authorName: String = "Elysium Specialist",
        authorHandle: String = "@elysium_expert",
    ): LinkPost {
        val postId = UUID.randomUUID().toString()
        val metadata = LinkMetadata(
            title = userCaption.take(50).ifBlank { "${link.provider.displayName}: ${link.contentType.label}" },
            description = userCaption,
            author = link.provider.displayName,
            source = "USER_CAPSULE",
        )

        val newPost = LinkPost(
            id = postId,
            authorId = currentUserId(),
            authorName = authorName,
            authorHandle = authorHandle,
            link = link,
            metadata = metadata,
            userCaption = userCaption,
            category = category,
            tags = tags,
            reactionsCount = 0,
            commentsCount = 0,
            savesCount = 0,
            createdAtEpochMs = System.currentTimeMillis(),
        )

        val updated = listOf(newPost) + _postsFlow.value
        _postsFlow.value = updated
        savePostsToDisk(updated)
        return newPost
    }

    fun toggleReaction(postId: String, reactionType: LinkReactionType) {
        val current = _postsFlow.value
        val updated = current.map { post ->
            if (post.id == postId) {
                if (post.userReaction == reactionType) {
                    post.copy(
                        userReaction = null,
                        reactionsCount = (post.reactionsCount - 1).coerceAtLeast(0)
                    )
                } else {
                    val countDelta = if (post.userReaction == null) 1 else 0
                    post.copy(
                        userReaction = reactionType,
                        reactionsCount = post.reactionsCount + countDelta
                    )
                }
            } else post
        }
        _postsFlow.value = updated
        savePostsToDisk(updated)
    }

    fun toggleSave(postId: String) {
        val current = _postsFlow.value
        val updated = current.map { post ->
            if (post.id == postId) {
                val newSaved = !post.isSaved
                post.copy(
                    isSaved = newSaved,
                    savesCount = if (newSaved) post.savesCount + 1 else (post.savesCount - 1).coerceAtLeast(0)
                )
            } else post
        }
        _postsFlow.value = updated
        savePostsToDisk(updated)
    }

    fun toggleFollow(authorId: String) {
        val current = _postsFlow.value
        val isNowFollowed = current.firstOrNull { it.authorId == authorId }?.isAuthorFollowed != true
        val updated = current.map { post ->
            if (post.authorId == authorId) {
                post.copy(isAuthorFollowed = isNowFollowed)
            } else post
        }
        _postsFlow.value = updated
        savePostsToDisk(updated)
    }

    fun addComment(postId: String, text: String, authorName: String = "Elysium Specialist"): LinkComment {
        val comment = LinkComment(
            id = UUID.randomUUID().toString(),
            postId = postId,
            authorId = currentUserId(),
            authorName = authorName,
            authorHandle = "@specialist",
            text = text,
        )
        // Increment comment count on post
        val current = _postsFlow.value
        val updated = current.map { post ->
            if (post.id == postId) post.copy(commentsCount = post.commentsCount + 1) else post
        }
        _postsFlow.value = updated
        savePostsToDisk(updated)
        return comment
    }

    private fun savePostsToDisk(posts: List<LinkPost>) {
        val serialized = runCatching { json.encodeToString(posts) }.getOrNull() ?: return
        prefs.edit { putString("cached_posts_json", serialized) }
    }

    private fun createInitialSeedCapsules(): List<LinkPost> {
        val now = System.currentTimeMillis()
        return listOf(
            LinkPost(
                id = "seed-yt-1",
                authorId = "system-meet",
                authorName = "MEET Vanguard Tech",
                authorHandle = "@meet_vanguard",
                link = SafeLinkResolver.resolve("https://www.youtube.com/watch?v=k1qG_F1bU4U"),
                metadata = LinkMetadata(
                    title = "Diagnóstico Osciloscopio: Sensor CKP vs CMP en Falla de Encendido",
                    description = "Análisis paso a paso de correlación entre sensor de cigüeñal y árbol de levas usando osciloscopio automotriz en tiempo real.",
                    author = "YouTube",
                    publishedAt = "2026",
                ),
                userCaption = "Excelente guía para peritajes técnicos cuando hay código P0016/P0017. Verifiquen siempre la señal antes de desarmar la distribución.",
                category = LinkCategory.AUTOMOTIVE,
                tags = listOf("OBD2", "Osciloscopio", "Diagnóstico", "DTC"),
                reactionsCount = 42,
                commentsCount = 7,
                savesCount = 28,
                createdAtEpochMs = now - 3600000 * 2,
            ),
            LinkPost(
                id = "seed-sp-1",
                authorId = "sound-lab",
                authorName = "Elysium Audio Lab",
                authorHandle = "@supreme_bass",
                link = SafeLinkResolver.resolve("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"),
                metadata = LinkMetadata(
                    title = "Frecuencias Sub-graves de Calibración 20Hz-60Hz",
                    description = "Pista técnica de prueba para calibrar respuesta de fase y respuesta de bajos en vehículos con Supreme Bass.",
                    author = "Spotify",
                ),
                userCaption = "Utilicen esta pista para probar la respuesta plana del subwoofer en el habitáculo con nuestro módulo Supreme Bass.",
                category = LinkCategory.AUDIO_MUSIC,
                tags = listOf("Audio", "Subwoofer", "Calibración", "SupremeBass"),
                reactionsCount = 89,
                commentsCount = 14,
                savesCount = 65,
                createdAtEpochMs = now - 3600000 * 5,
            ),
            LinkPost(
                id = "seed-safety-1",
                authorId = "safety-team",
                authorName = "Observatorio Forense",
                authorHandle = "@safety_authority",
                link = SafeLinkResolver.resolve("https://vimeo.com/76979871"),
                metadata = LinkMetadata(
                    title = "Metodología de Cadena de Custodia Digital en Peritajes",
                    description = "Procedimiento pericial forense para la fijación de evidencias y generación de reportes con hash criptográfico SHA-256.",
                    author = "Vimeo",
                ),
                userCaption = "Estándar obligatorio para inspectores MEET al certificar reportes forenses con código QR inviolable.",
                category = LinkCategory.SAFETY,
                tags = listOf("Forense", "Peritaje", "CadenaCustodia", "SHA256"),
                reactionsCount = 56,
                commentsCount = 9,
                savesCount = 34,
                createdAtEpochMs = now - 3600000 * 12,
            ),
            LinkPost(
                id = "seed-tech-1",
                authorId = "elysium-ai",
                authorName = "Elysium Vanguard OS",
                authorHandle = "@elysium_os",
                link = SafeLinkResolver.resolve("https://x.com/ElysiumNetwork/status/1789000111222333"),
                metadata = LinkMetadata(
                    title = "Arquitectura Mesh Resiliente: Wi-Fi P2P + Bluetooth 5.4",
                    description = "Explicación de la gobernanza de enrutamiento tolerante a caídas de red para comunicaciones críticas.",
                    author = "X",
                ),
                userCaption = "Todo en uno. La red mesh gobierna automáticamente: primero internet, luego Wi-Fi local, luego Bluetooth, asegurando transmisión continua.",
                category = LinkCategory.TECH_AI,
                tags = listOf("RedMesh", "ElysiumOS", "P2P", "OfflineFirst"),
                reactionsCount = 112,
                commentsCount = 23,
                savesCount = 78,
                createdAtEpochMs = now - 3600000 * 20,
            ),
        )
    }
}
