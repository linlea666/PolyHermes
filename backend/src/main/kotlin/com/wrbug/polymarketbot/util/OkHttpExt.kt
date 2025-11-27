package com.wrbug.polymarketbot.util

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * 获取代理配置（用于 WebSocket 和 HTTP 请求）
 * @return Proxy 对象，如果未启用代理则返回 null
 */
fun getProxyConfig(): Proxy? {
    if (getEnv("ENABLE_PROXY") != "1") {
        return null
    }
    val host = getEnv("PROXY_HOST").ifEmpty { "127.0.0.1" }
    val port = getEnv("PROXY_PORT").toIntOrNull() ?: 8888
    return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
}

/**
 * 创建OkHttpClient客户端
 * @return OkHttpClient.Builder
 */
fun createClient() = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .httpProxy("127.0.0.1", 8888)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)

/**
 * 为OkHttpClient添加HTTP代理支持
 * @param hostname 代理服务器地址
 * @param port 代理服务器端口
 * @param user 代理用户名（可选）
 * @param password 代理密码（可选）
 * @return OkHttpClient.Builder
 */
fun OkHttpClient.Builder.httpProxy(
    hostname: String, port: Int, user: String = "", password: String = ""
): OkHttpClient.Builder {
    if (getEnv("ENABLE_PROXY") != "1") {
        return this
    }
    return apply {
        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(hostname, port)))
        createSSLSocketFactory()
        if (user.isNotEmpty() && password.isNotEmpty()) {
            proxyAuthenticator { _, res ->
                val credential: String = Credentials.basic(user, password)
                res.request.newBuilder().header("Proxy-Authorization", credential).build()
            }
        }
    }
}

/**
 * 为OkHttpClient创建信任所有证书的SSL工厂
 * @return OkHttpClient.Builder
 */
fun OkHttpClient.Builder.createSSLSocketFactory(): OkHttpClient.Builder {
    runCatching {
        val sc: SSLContext = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf<TrustManager>(TrustAllManager()), SecureRandom())
        this.sslSocketFactory(sc.socketFactory, TrustAllManager())
    }
    return this
}

/**
 * 信任所有证书的TrustManager
 */
class TrustAllManager : X509TrustManager {
    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
    }

    override fun getAcceptedIssuers() = arrayOfNulls<X509Certificate>(0)
}

/**
 * 信任所有主机名的HostnameVerifier
 */
class TrustAllHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        return true
    }
}

