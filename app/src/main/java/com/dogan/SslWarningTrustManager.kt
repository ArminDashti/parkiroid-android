package com.dogan

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Permissive SSL that logs certificate warnings instead of blocking connections. */
object SslWarningTrustManager {
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) return
            val cert = chain[0]
            AppLogger.warn(
                "SSL",
                "Certificate accepted with warning: subject=${cert.subjectX500Principal.name}",
            )
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun createSocketFactory(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        return context.socketFactory
    }

    val hostnameVerifier = HostnameVerifier { _, _ -> true }
}
