package com.elysium369.meet.linksphere.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.elysium369.meet.linksphere.domain.ExternalOpenResult
import com.elysium369.meet.linksphere.domain.NormalizedLink

/**
 * Lanzador Centralizado y Seguro de Enlaces Externos para Android.
 * Evita fallos de ActivityNotFoundException, valida intents y
 * retorna un resultado tipado sin falsos positivos.
 */
object SafeLinkLauncher {

    fun open(context: Context, link: NormalizedLink): ExternalOpenResult {
        val targetUrl = link.canonicalUrl.ifBlank { link.originalUrl }
        return openUri(context, targetUrl)
    }

    fun openUri(context: Context, rawUrl: String): ExternalOpenResult {
        if (rawUrl.isBlank()) return ExternalOpenResult.INVALID_URI

        val parsed = try {
            Uri.parse(rawUrl)
        } catch (e: Exception) {
            return ExternalOpenResult.INVALID_URI
        }

        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ExternalOpenResult.SECURITY_REJECTED
        }

        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ExternalOpenResult.OPENED
        } catch (e: ActivityNotFoundException) {
            ExternalOpenResult.NO_HANDLER
        } catch (e: SecurityException) {
            ExternalOpenResult.SECURITY_REJECTED
        } catch (e: Exception) {
            ExternalOpenResult.FAILED
        }
    }
}
