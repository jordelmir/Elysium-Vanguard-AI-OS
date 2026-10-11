package com.elysium369.meet.linksphere.domain

import kotlinx.serialization.Serializable

/**
 * ELYSIUM LINKSPHERE — Modelos Universales de Dominio Social de Enlaces.
 * Red social universal de enlaces integrada en Elysium Vanguard AI OS.
 *
 * Principio Fundamental:
 * Elysium almacena identidad social, publicaciones, relaciones, metadatos mínimos,
 * preferencias, estados de moderación y métricas agregadas; los medios continúan
 * alojados por sus proveedores originales.
 */

@Serializable
enum class LinkProvider(val displayName: String, val iconEmoji: String) {
    YOUTUBE("YouTube", "▶️"),
    TIKTOK("TikTok", "🎵"),
    SPOTIFY("Spotify", "🎧"),
    TWITCH("Twitch", "🟣"),
    VIMEO("Vimeo", "🎬"),
    SOUNDCLOUD("SoundCloud", "☁️"),
    DAILYMOTION("Dailymotion", "📺"),
    FACEBOOK("Facebook", "👥"),
    INSTAGRAM("Instagram", "📷"),
    X_TWITTER("X / Twitter", "𝕏"),
    REDDIT("Reddit", "🤖"),
    PINTEREST("Pinterest", "📌"),
    BLUESKY("Bluesky", "🦋"),
    LINKEDIN("LinkedIn", "💼"),
    GENERIC_WEB("Web Universal", "🌐"),
}

@Serializable
enum class ProviderContentType(val label: String) {
    VIDEO("Video"),
    SHORT_VIDEO("Short / Reel"),
    LIVE_STREAM("Directo"),
    POST("Publicación"),
    REEL("Reel"),
    IMAGE("Imagen"),
    AUDIO("Audio"),
    TRACK("Pista"),
    ALBUM("Álbum"),
    PLAYLIST("Lista"),
    PODCAST("Podcast"),
    ARTICLE("Artículo"),
    PRODUCT("Producto"),
    GENERIC_WEBPAGE("Página Web"),
    UNKNOWN("Desconocido"),
}

@Serializable
enum class LinkResolution(val label: String, val allowsInlinePlay: Boolean) {
    PLAYABLE_EMBED("Reproducción Interna Oficial", true),
    OFFICIAL_POST_EMBED("Embed Oficial de Publicación", true),
    METADATA_CARD("Vista Previa Verificada", false),
    EXTERNAL_ONLY("Apertura Externa Requerida", false),
    INVALID("Enlace No Válido", false),
    BLOCKED_BY_POLICY("Bloqueado por Seguridad", false),
    PROVIDER_UNAVAILABLE("Proveedor No Disponible", false),
}

@Serializable
data class ProviderCapabilities(
    val supportsInlinePlayback: Boolean = false,
    val supportsPostEmbed: Boolean = false,
    val supportsMetadata: Boolean = true,
    val requiresApiCredential: Boolean = false,
    val requiresVerifiedOrigin: Boolean = false,
    val supportsDeepLink: Boolean = true,
    val supportsExternalOpen: Boolean = true,
)

@Serializable
data class NormalizedLink(
    val originalUrl: String,
    val canonicalUrl: String,
    val host: String,
    val provider: LinkProvider,
    val contentId: String? = null,
    val secondaryId: String? = null,
    val contentType: ProviderContentType = ProviderContentType.UNKNOWN,
    val resolution: LinkResolution = LinkResolution.METADATA_CARD,
    val uncertaintyLabel: String? = null,
)

@Serializable
data class LinkMetadata(
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val authorUrl: String? = null,
    val thumbnailUrl: String? = null,
    val publishedAt: String? = null,
    val iconUrl: String? = null,
    val language: String? = null,
    val source: String = "OBSERVED",
)

@Serializable
enum class PreviewStatus {
    NOT_REQUESTED,
    QUEUED,
    FETCHING,
    READY,
    PARTIAL,
    UNAVAILABLE,
    BLOCKED,
    EXPIRED,
    ERROR,
}

@Serializable
enum class PlaybackPreference(val label: String) {
    ASK_EVERY_TIME("Preguntar siempre"),
    IN_APP_WHEN_SUPPORTED("Reproducir dentro si está soportado"),
    EXTERNAL_APP("Abrir en app externa / navegador"),
}

@Serializable
enum class ExternalOpenResult {
    OPENED,
    NO_HANDLER,
    INVALID_URI,
    SECURITY_REJECTED,
    FAILED,
}

@Serializable
enum class LinkReactionType(val emoji: String, val label: String) {
    LIKE("👍", "Me gusta"),
    LOVE("❤️", "Me encanta"),
    FIRE("🔥", "Fuego"),
    BRAIN("🧠", "Inteligente"),
    ROCKET("🚀", "Al infinito"),
}

@Serializable
data class LinkReaction(
    val type: LinkReactionType,
    val count: Int = 0,
    val userSelected: Boolean = false,
)

@Serializable
data class LinkComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorHandle: String,
    val text: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class LinkCollection(
    val id: String,
    val title: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val itemsCount: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
enum class LinkCategory(val title: String, val iconEmoji: String) {
    ALL("Todo", "✨"),
    AUTOMOTIVE("Mecánica & Autos", "🏎️"),
    SAFETY("Seguridad & Peritaje", "🛡️"),
    TECH_AI("Tecnología & IA", "⚡"),
    AUDIO_MUSIC("Audio & Música", "🎵"),
    VIDEO_PODCAST("Video & Podcasts", "🎬"),
    TOOLS_EQUIPMENT("Herramientas", "🛠️"),
    SCIENCE("Ciencia & Vanguardia", "🔬"),
}

@Serializable
enum class LinkFeedTab(val title: String) {
    FOR_YOU("Para Ti"),
    FOLLOWING("Siguiendo"),
    TRENDING("Tendencias"),
    CATEGORIES("Categorías"),
}

@Serializable
data class LinkPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String? = null,
    val link: NormalizedLink,
    val metadata: LinkMetadata,
    val userCaption: String,
    val category: LinkCategory = LinkCategory.AUTOMOTIVE,
    val tags: List<String> = emptyList(),
    val reactionsCount: Int = 0,
    val commentsCount: Int = 0,
    val savesCount: Int = 0,
    val userReaction: LinkReactionType? = null,
    val isSaved: Boolean = false,
    val isAuthorFollowed: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val embedHtmlUrl: String? = null,
)
