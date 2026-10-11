package com.elysium369.meet.linksphere.domain

import android.net.Uri

/**
 * Resolvedor y Sanitizador Seguro de Enlaces Universal (SafeLinkResolver).
 * Frontera de seguridad contra SSRF, inyección de esquemas, manipulación de query
 * y suplantación de proveedores.
 */
object SafeLinkResolver {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "igshid", "si", "_hsenc", "_hsmi", "mc_cid", "mc_eid"
    )

    fun resolve(rawInput: String): NormalizedLink {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank() || trimmed.length > 2048) {
            return invalidLink(rawInput, "Longitud o formato no permitido")
        }

        // Validación de caracteres de control
        if (trimmed.any { it.isISOControl() }) {
            return invalidLink(rawInput, "Contiene caracteres de control no autorizados")
        }

        val parsedUri = try {
            Uri.parse(trimmed)
        } catch (e: Exception) {
            return invalidLink(rawInput, "URI malformada")
        }

        val scheme = parsedUri.scheme?.lowercase() ?: ""
        if (scheme != "http" && scheme != "https") {
            return invalidLink(rawInput, "Esquema no permitido ($scheme). Solo se admite HTTPS/HTTP.")
        }

        val host = parsedUri.host?.lowercase() ?: return invalidLink(rawInput, "Host no identificado")
        if (parsedUri.userInfo != null) {
            return invalidLink(rawInput, "Credenciales en la URL rechazadas por seguridad")
        }

        // Limpieza de parámetros de rastreo preservando parámetros funcionales
        val sanitizedUri = sanitizeQuery(parsedUri)
        val canonicalUrl = sanitizedUri.toString()

        return classifyLink(rawInput, canonicalUrl, host, sanitizedUri)
    }

    private fun sanitizeQuery(uri: Uri): Uri {
        if (!uri.isHierarchical) return uri
        val queryNames = try {
            uri.queryParameterNames
        } catch (e: Exception) {
            return uri
        }
        if (queryNames.none { it.lowercase() in TRACKING_PARAMS }) {
            return uri
        }
        val builder = uri.buildUpon().clearQuery()
        for (param in queryNames) {
            if (param.lowercase() !in TRACKING_PARAMS) {
                uri.getQueryParameters(param).forEach { value ->
                    builder.appendQueryParameter(param, value)
                }
            }
        }
        return builder.build()
    }

    private fun classifyLink(
        originalUrl: String,
        canonicalUrl: String,
        host: String,
        uri: Uri,
    ): NormalizedLink {
        val path = uri.path ?: ""

        // 1. YouTube
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
            val (contentId, contentType) = when {
                host == "youtu.be" -> {
                    val id = path.trim('/').substringBefore('/')
                    id to ProviderContentType.VIDEO
                }
                path.startsWith("/shorts/") -> {
                    val id = path.removePrefix("/shorts/").trim('/').substringBefore('/')
                    id to ProviderContentType.SHORT_VIDEO
                }
                path.startsWith("/embed/") -> {
                    val id = path.removePrefix("/embed/").trim('/').substringBefore('/')
                    id to ProviderContentType.VIDEO
                }
                path.startsWith("/live/") -> {
                    val id = path.removePrefix("/live/").trim('/').substringBefore('/')
                    id to ProviderContentType.LIVE_STREAM
                }
                path.contains("playlist") -> {
                    val listId = uri.getQueryParameter("list") ?: ""
                    listId to ProviderContentType.PLAYLIST
                }
                else -> {
                    val v = uri.getQueryParameter("v") ?: ""
                    v to ProviderContentType.VIDEO
                }
            }
            val resolution = if (contentId.isNotBlank()) LinkResolution.PLAYABLE_EMBED else LinkResolution.METADATA_CARD
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.YOUTUBE,
                contentId = contentId.ifBlank { null },
                contentType = contentType,
                resolution = resolution,
            )
        }

        // 2. Spotify
        if (host == "open.spotify.com" || host.endsWith(".spotify.com")) {
            val segments = path.split('/').filter { it.isNotBlank() }
            val (type, id) = if (segments.size >= 2) {
                val t = when (segments[0].lowercase()) {
                    "track" -> ProviderContentType.TRACK
                    "album" -> ProviderContentType.ALBUM
                    "playlist" -> ProviderContentType.PLAYLIST
                    "episode" -> ProviderContentType.PODCAST
                    else -> ProviderContentType.AUDIO
                }
                t to segments[1]
            } else {
                ProviderContentType.AUDIO to null
            }
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.SPOTIFY,
                contentId = id,
                contentType = type,
                resolution = if (id != null) LinkResolution.PLAYABLE_EMBED else LinkResolution.METADATA_CARD,
            )
        }

        // 3. Vimeo
        if (host == "vimeo.com" || host.endsWith(".vimeo.com")) {
            val id = path.trim('/').substringBefore('/').filter { it.isDigit() }
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.VIMEO,
                contentId = id.ifBlank { null },
                contentType = ProviderContentType.VIDEO,
                resolution = if (id.isNotBlank()) LinkResolution.PLAYABLE_EMBED else LinkResolution.METADATA_CARD,
            )
        }

        // 4. Twitch
        if (host == "twitch.tv" || host.endsWith(".twitch.tv") || host == "clips.twitch.tv") {
            val (id, type) = when {
                path.startsWith("/videos/") -> {
                    path.removePrefix("/videos/").trim('/').substringBefore('/') to ProviderContentType.VIDEO
                }
                host == "clips.twitch.tv" || path.contains("/clip/") -> {
                    path.trim('/').substringAfterLast('/') to ProviderContentType.SHORT_VIDEO
                }
                else -> {
                    val channel = path.trim('/').substringBefore('/')
                    channel to ProviderContentType.LIVE_STREAM
                }
            }
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.TWITCH,
                contentId = id.ifBlank { null },
                contentType = type,
                resolution = if (id.isNotBlank()) LinkResolution.PLAYABLE_EMBED else LinkResolution.METADATA_CARD,
            )
        }

        // 5. SoundCloud
        if (host == "soundcloud.com" || host.endsWith(".soundcloud.com")) {
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.SOUNDCLOUD,
                contentId = path.trim('/'),
                contentType = ProviderContentType.TRACK,
                resolution = LinkResolution.PLAYABLE_EMBED,
            )
        }

        // 6. Dailymotion
        if (host == "dailymotion.com" || host.endsWith(".dailymotion.com") || host == "dai.ly") {
            val id = if (host == "dai.ly") {
                path.trim('/')
            } else {
                path.substringAfter("/video/").trim('/').substringBefore('/')
            }
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.DAILYMOTION,
                contentId = id.ifBlank { null },
                contentType = ProviderContentType.VIDEO,
                resolution = if (id.isNotBlank()) LinkResolution.PLAYABLE_EMBED else LinkResolution.METADATA_CARD,
            )
        }

        // 7. TikTok
        if (host == "tiktok.com" || host.endsWith(".tiktok.com")) {
            val videoId = path.substringAfter("/video/").substringBefore('?').substringBefore('/')
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.TIKTOK,
                contentId = videoId.ifBlank { null },
                contentType = ProviderContentType.SHORT_VIDEO,
                resolution = LinkResolution.OFFICIAL_POST_EMBED,
            )
        }

        // 8. X / Twitter
        if (host == "twitter.com" || host.endsWith(".twitter.com") || host == "x.com" || host.endsWith(".x.com")) {
            val statusId = path.substringAfter("/status/").substringBefore('?').substringBefore('/')
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.X_TWITTER,
                contentId = statusId.ifBlank { null },
                contentType = ProviderContentType.POST,
                resolution = LinkResolution.OFFICIAL_POST_EMBED,
            )
        }

        // 9. Reddit
        if (host == "reddit.com" || host.endsWith(".reddit.com")) {
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.REDDIT,
                contentId = path.substringAfter("/comments/").substringBefore('/'),
                contentType = ProviderContentType.POST,
                resolution = LinkResolution.METADATA_CARD,
            )
        }

        // 10. Instagram
        if (host == "instagram.com" || host.endsWith(".instagram.com")) {
            val id = path.substringAfter("/p/").substringAfter("/reel/").trim('/').substringBefore('/')
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.INSTAGRAM,
                contentId = id.ifBlank { null },
                contentType = if (path.contains("/reel/")) ProviderContentType.REEL else ProviderContentType.POST,
                resolution = LinkResolution.OFFICIAL_POST_EMBED,
            )
        }

        // 11. Bluesky
        if (host == "bsky.app" || host.endsWith(".bsky.app")) {
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.BLUESKY,
                contentId = path.substringAfter("/post/").substringBefore('/'),
                contentType = ProviderContentType.POST,
                resolution = LinkResolution.METADATA_CARD,
            )
        }

        // 12. LinkedIn
        if (host == "linkedin.com" || host.endsWith(".linkedin.com")) {
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.LINKEDIN,
                contentId = path.trim('/'),
                contentType = ProviderContentType.POST,
                resolution = LinkResolution.METADATA_CARD,
            )
        }

        // 13. Pinterest
        if (host == "pinterest.com" || host.endsWith(".pinterest.com") || host == "pin.it") {
            return NormalizedLink(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                host = host,
                provider = LinkProvider.PINTEREST,
                contentId = path.trim('/'),
                contentType = ProviderContentType.IMAGE,
                resolution = LinkResolution.METADATA_CARD,
            )
        }

        // Fallback: Web Universal
        return NormalizedLink(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            host = host,
            provider = LinkProvider.GENERIC_WEB,
            contentId = null,
            contentType = ProviderContentType.GENERIC_WEBPAGE,
            resolution = LinkResolution.METADATA_CARD,
        )
    }

    private fun invalidLink(url: String, reason: String): NormalizedLink {
        return NormalizedLink(
            originalUrl = url,
            canonicalUrl = url,
            host = "",
            provider = LinkProvider.GENERIC_WEB,
            contentId = null,
            contentType = ProviderContentType.UNKNOWN,
            resolution = LinkResolution.INVALID,
            uncertaintyLabel = reason,
        )
    }
}
