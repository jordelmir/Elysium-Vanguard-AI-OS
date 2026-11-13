package com.elysium.nexus.core.transport.mac

import java.util.UUID

/** A compact, copyable invite. The 256-bit secret is never sent to the service. */
data class NexusPairingCode(val sessionId: String, val secretBase64Url: String) {
    fun encode(): String = "elysium-nexus://pair/$sessionId/$secretBase64Url"

    companion object {
        fun parse(value: String): NexusPairingCode? = runCatching {
            val uri = java.net.URI(value.trim())
            require(uri.scheme == "elysium-nexus" && uri.host == "pair")
            require(uri.query == null && uri.fragment == null)
            val parts = uri.path.split('/').filter(String::isNotBlank)
            require(parts.size == 2)
            val sessionId = UUID.fromString(parts[0]).toString()
            require(sessionId == parts[0].lowercase())
            require(parts[1].matches(Regex("[A-Za-z0-9_-]{43}")))
            NexusPairingCode(sessionId, parts[1])
        }.getOrNull()
    }
}
