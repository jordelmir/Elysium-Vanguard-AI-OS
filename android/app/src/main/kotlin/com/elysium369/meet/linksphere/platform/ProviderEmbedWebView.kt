package com.elysium369.meet.linksphere.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.elysium369.meet.linksphere.domain.NormalizedLink
import com.elysium369.meet.linksphere.domain.ProviderRegistry
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Visor/Reproductor Aislado Oficial de Embeds (ProviderEmbedWebView).
 * Cumple los contratos estrictos de seguridad de Elysium LinkSphere:
 * - Acceso a archivos y content deshabilitado.
 * - Sin puentes JavaScript arbitrarios.
 * - Ciclo de vida limpio sin fugas de memoria.
 * - Controles visuales Cyber Neon integrados.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProviderEmbedWebView(
    link: NormalizedLink,
    modifier: Modifier = Modifier,
    onOpenExternal: () -> Unit,
) {
    val context = LocalContext.current
    val embedUrl = remember(link) { ProviderRegistry.buildOfficialEmbedUrl(link) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF070E17),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (embedUrl == null || hasError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎬", fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Reproducción en ${link.provider.displayName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "El proveedor requiere visualización en su plataforma oficial",
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenExternal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "ABRIR EN ${link.provider.displayName.uppercase()}",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createSecureWebView(ctx, embedUrl) { state ->
                            when (state) {
                                is WebViewLoadState.Loading -> isLoading = true
                                is WebViewLoadState.Finished -> isLoading = false
                                is WebViewLoadState.Error -> {
                                    isLoading = false
                                    hasError = true
                                }
                            }
                        }
                    },
                    update = { webView ->
                        if (webView.url != embedUrl) {
                            webView.loadUrl(embedUrl)
                        }
                    }
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xD90A131F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MeetColors.neonGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Cargando ${link.provider.displayName}...",
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class WebViewLoadState {
    object Loading : WebViewLoadState()
    object Finished : WebViewLoadState()
    object Error : WebViewLoadState()
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSecureWebView(
    context: Context,
    initialUrl: String,
    onStateChange: (WebViewLoadState) -> Unit,
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true // Necesario para iframes oficiales de YouTube/Spotify
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webChromeClient = WebChromeClient()

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onStateChange(WebViewLoadState.Loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onStateChange(WebViewLoadState.Finished)
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                onStateChange(WebViewLoadState.Error)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                // Permite la carga del propio iframe embed, bloquea navegación externa fuera de iframe
                val host = targetUri.host?.lowercase() ?: ""
                val isAllowedHost = host.contains("youtube") || host.contains("spotify") ||
                        host.contains("vimeo") || host.contains("twitch") || host.contains("dailymotion") ||
                        host.contains("soundcloud")
                return !isAllowedHost
            }
        }

        loadUrl(initialUrl)
    }
}
