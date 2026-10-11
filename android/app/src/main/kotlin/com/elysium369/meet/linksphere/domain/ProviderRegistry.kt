package com.elysium369.meet.linksphere.domain

/**
 * Registro de Proveedores y Capacidades de Elysium LinkSphere.
 * Proporciona metadatos versionados, validación de dominios y
 * generadores de URL de embed oficiales seguros con aislamiento de origen.
 */
object ProviderRegistry {

    private val capabilitiesMap = mapOf(
        LinkProvider.YOUTUBE to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.SPOTIFY to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.VIMEO to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.TWITCH to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = true,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.SOUNDCLOUD to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.DAILYMOTION to ProviderCapabilities(
            supportsInlinePlayback = true,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.TIKTOK to ProviderCapabilities(
            supportsInlinePlayback = false, // TikTok embed oficial requiere iframe SDK pesado o external app
            supportsPostEmbed = true,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.X_TWITTER to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = true,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.REDDIT to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.INSTAGRAM to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = true,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.FACEBOOK to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.BLUESKY to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.LINKEDIN to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.PINTEREST to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = true,
            supportsExternalOpen = true,
        ),
        LinkProvider.GENERIC_WEB to ProviderCapabilities(
            supportsInlinePlayback = false,
            supportsPostEmbed = false,
            supportsMetadata = true,
            requiresApiCredential = false,
            requiresVerifiedOrigin = false,
            supportsDeepLink = false,
            supportsExternalOpen = true,
        ),
    )

    fun getCapabilities(provider: LinkProvider): ProviderCapabilities {
        return capabilitiesMap[provider] ?: ProviderCapabilities()
    }

    /**
     * Construye la URL de embed oficial aprobada por el proveedor.
     * Nunca se usa la URL cruda de usuario como src de iframe.
     */
    fun buildOfficialEmbedUrl(link: NormalizedLink): String? {
        val id = link.contentId ?: return null
        return when (link.provider) {
            LinkProvider.YOUTUBE -> {
                // youtube-nocookie para máxima privacidad
                "https://www.youtube-nocookie.com/embed/$id?autoplay=0&rel=0&modestbranding=1"
            }
            LinkProvider.SPOTIFY -> {
                val type = when (link.contentType) {
                    ProviderContentType.ALBUM -> "album"
                    ProviderContentType.PLAYLIST -> "playlist"
                    ProviderContentType.PODCAST -> "episode"
                    else -> "track"
                }
                "https://open.spotify.com/embed/$type/$id?utm_source=generator&theme=0"
            }
            LinkProvider.VIMEO -> {
                "https://player.vimeo.com/video/$id?dnt=1&autoplay=0"
            }
            LinkProvider.TWITCH -> {
                if (link.contentType == ProviderContentType.LIVE_STREAM) {
                    "https://player.twitch.tv/?channel=$id&parent=localhost&autoplay=false"
                } else {
                    "https://player.twitch.tv/?video=$id&parent=localhost&autoplay=false"
                }
            }
            LinkProvider.DAILYMOTION -> {
                "https://www.dailymotion.com/embed/video/$id?autoplay=0"
            }
            LinkProvider.SOUNDCLOUD -> {
                val encodedUrl = java.net.URLEncoder.encode(link.canonicalUrl, "UTF-8")
                "https://w.soundcloud.com/player/?url=$encodedUrl&color=%2300ffd5&auto_play=false&hide_related=true&show_comments=false&show_user=true&show_reposts=false&show_teaser=false"
            }
            else -> null
        }
    }
}
