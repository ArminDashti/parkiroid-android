package com.dogan

import java.net.URI

/** Builds API and stream URLs from endpoint host/port settings. */
object EndpointUrlBuilder {
    fun buildApiBaseUrl(endpoint: String, port: Int): String {
        val host = endpoint.trim().trimEnd('/')
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host.trimEnd('/')
        }
        val scheme = if (port == 443) "https" else "http"
        val portSuffix = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
            ""
        } else {
            ":$port"
        }
        return "$scheme://$host$portSuffix/dogan"
    }

    fun buildStreamUrl(endpoint: String, port: Int): String {
        val host = endpoint.trim().trimEnd('/')
        if (host.startsWith("ws://") || host.startsWith("wss://")) return host
        val scheme = if (port == 443) "wss" else "ws"
        val portSuffix = if ((scheme == "wss" && port == 443) || (scheme == "ws" && port == 80)) {
            ""
        } else {
            ":$port"
        }
        return "$scheme://$host$portSuffix"
    }

    fun parseHostFromUrl(url: String): String {
        return try {
            val uri = URI(url.trim())
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    fun defaultEndpointFromLegacyUrl(serverBaseUrl: String): String {
        return try {
            val uri = URI(serverBaseUrl.trim())
            uri.host ?: "dogan-api.xaigrok.ir"
        } catch (_: Exception) {
            "dogan-api.xaigrok.ir"
        }
    }

    fun defaultPortFromLegacyUrl(serverBaseUrl: String): Int {
        return try {
            val uri = URI(serverBaseUrl.trim())
            when {
                uri.port > 0 -> uri.port
                uri.scheme == "https" -> 443
                else -> 8090
            }
        } catch (_: Exception) {
            8090
        }
    }
}
