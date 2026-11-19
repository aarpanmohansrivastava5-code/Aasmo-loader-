package com.example.data.media

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class UrlSecurityPolicy {
    /**
     * Production policy: strictly rejects localhost, loopback, private RFC1918, CGNAT,
     * link-local, cloud metadata, multicast, ULA IPv6, unspecified addresses, and unsafe redirects.
     */
    STRICT,

    /**
     * Test-only policy enabling loopback HTTP test servers (127.0.0.1 / ::1 / localhost)
     * while continuing to enforce scheme checks, private/RFC1918/metadata blocks, and redirect loop limits.
     */
    LOOPBACK_TEST,
}

sealed interface UrlValidationResult {
    data class Valid(val httpUrl: HttpUrl) : UrlValidationResult
    data class Rejected(val reason: String) : UrlValidationResult

    val isValid: Boolean get() = this is Valid
}

object UrlSecurityValidator {
    const val MAX_REDIRECT_HOPS = 5

    private val blockedLocalHostnames = setOf(
        "localhost",
        "localhost.localdomain",
        "ip6-localhost",
        "ip6-loopback",
        "metadata.google.internal",
        "metadata",
    )

    private val blockedSuffixes = setOf(
        ".localhost",
        ".local",
        ".internal",
        ".localdomain",
    )

    fun validateSyntaxAndHost(
        rawUrl: String,
        policy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return UrlValidationResult.Rejected("URL is blank.")
        }

        val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(trimmed)
        if (schemeMatch != null) {
            val scheme = schemeMatch.groupValues[1].lowercase()
            if (scheme != "http" && scheme != "https") {
                return UrlValidationResult.Rejected("Unsupported or unsafe URL scheme '$scheme'.")
            }
        }

        val httpUrl = trimmed.toHttpUrlOrNull()
            ?: return UrlValidationResult.Rejected("Malformed URL.")

        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            return UrlValidationResult.Rejected("Only http and https URLs are allowed.")
        }

        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) {
            return UrlValidationResult.Rejected("Embedded credentials in URLs are not permitted.")
        }

        val host = httpUrl.host.trimEnd('.').lowercase()
        if (host.isBlank() || host.startsWith('.') || host.contains("..")) {
            return UrlValidationResult.Rejected("Invalid URL host.")
        }

        val isLoopbackHostname = host == "localhost" || host == "ip6-localhost" || host == "ip6-loopback" ||
            host.endsWith(".localhost")
        if (isLoopbackHostname && policy != UrlSecurityPolicy.LOOPBACK_TEST) {
            return UrlValidationResult.Rejected("Access to local host '$host' is blocked for security.")
        }

        if (!isLoopbackHostname && (host in blockedLocalHostnames || blockedSuffixes.any { host.endsWith(it) })) {
            return UrlValidationResult.Rejected("Access to internal host '$host' is blocked for security.")
        }

        // If host is an IP literal (v4 or bracketless v6 parsed by OkHttp), validate IP immediately without DNS
        parseIpLiteralOrNull(host)?.let { literalAddr ->
            val ipCheck = validateResolvedAddress(literalAddr, policy)
            if (ipCheck != null) {
                return UrlValidationResult.Rejected(ipCheck)
            }
        } ?: run {
            // Non-IP hostname must contain a dot (single-label names like "intranet" are unsafe)
            if (!host.contains('.') && policy != UrlSecurityPolicy.LOOPBACK_TEST) {
                return UrlValidationResult.Rejected("Single-label hostnames are not permitted.")
            }
        }

        return UrlValidationResult.Valid(httpUrl)
    }

    fun validateWithDns(
        rawUrl: String,
        policy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): UrlValidationResult {
        val syntaxResult = validateSyntaxAndHost(rawUrl, policy)
        if (syntaxResult !is UrlValidationResult.Valid) return syntaxResult

        val host = syntaxResult.httpUrl.host
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            return UrlValidationResult.Rejected("DNS lookup failed for '$host'.")
        } catch (e: Exception) {
            return UrlValidationResult.Rejected("DNS validation error: ${e.localizedMessage ?: "unknown"}")
        }

        if (addresses.isEmpty()) {
            return UrlValidationResult.Rejected("Host '$host' resolved to no addresses.")
        }

        for (addr in addresses) {
            val violation = validateResolvedAddress(addr, policy)
            if (violation != null) {
                return UrlValidationResult.Rejected(violation)
            }
        }
        return syntaxResult
    }

    fun validateResolvedAddress(
        address: InetAddress,
        policy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
    ): String? {
        if (address.isLoopbackAddress) {
            return if (policy == UrlSecurityPolicy.LOOPBACK_TEST) {
                null
            } else {
                "Blocked loopback address (${address.hostAddress})."
            }
        }

        if (address.isAnyLocalAddress) {
            return "Blocked unspecified/any-local address (${address.hostAddress})."
        }
        if (address.isLinkLocalAddress) {
            return "Blocked link-local address (${address.hostAddress})."
        }
        if (address.isSiteLocalAddress) {
            return "Blocked private/site-local address (${address.hostAddress})."
        }
        if (address.isMulticastAddress) {
            return "Blocked multicast address (${address.hostAddress})."
        }

        return when (address) {
            is Inet4Address -> validateIpv4Bytes(address.address, policy)
            is Inet6Address -> validateIpv6Bytes(address.address, policy)
            else -> "Unsupported address family."
        }
    }

    private fun validateIpv4Bytes(bytes: ByteArray, policy: UrlSecurityPolicy): String? {
        if (bytes.size != 4) return "Invalid IPv4 address."
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF

        return when {
            b0 == 0 -> "Blocked 0.0.0.0/8 network."
            b0 == 10 -> "Blocked private 10.0.0.0/8 address."
            b0 == 100 && b1 in 64..127 -> "Blocked CGNAT 100.64.0.0/10 address."
            b0 == 127 -> if (policy == UrlSecurityPolicy.LOOPBACK_TEST) null else "Blocked loopback 127.0.0.0/8 address."
            b0 == 169 && b1 == 254 -> "Blocked link-local/metadata 169.254.0.0/16 address."
            b0 == 172 && b1 in 16..31 -> "Blocked private 172.16.0.0/12 address."
            b0 == 192 && b1 == 0 && b2 == 0 -> "Blocked IETF protocol 192.0.0.0/24 address."
            b0 == 192 && b1 == 0 && b2 == 2 -> "Blocked TEST-NET-1 192.0.2.0/24 address."
            b0 == 192 && b1 == 168 -> "Blocked private 192.168.0.0/16 address."
            b0 == 198 && b1 in 18..19 -> "Blocked benchmarking 198.18.0.0/15 address."
            b0 == 198 && b1 == 51 && b2 == 100 -> "Blocked TEST-NET-2 address."
            b0 == 203 && b1 == 0 && b2 == 113 -> "Blocked TEST-NET-3 address."
            b0 >= 224 -> "Blocked multicast/reserved IPv4 address."
            else -> null
        }
    }

    private fun validateIpv6Bytes(bytes: ByteArray, policy: UrlSecurityPolicy): String? {
        if (bytes.size != 16) return "Invalid IPv6 address."

        // Unspecified ::
        if (bytes.all { it == 0.toByte() }) {
            return "Blocked unspecified IPv6 address (::)."
        }

        // Loopback ::1
        val isV6Loopback = (0 until 15).all { bytes[it] == 0.toByte() } && bytes[15] == 1.toByte()
        if (isV6Loopback) {
            return if (policy == UrlSecurityPolicy.LOOPBACK_TEST) null else "Blocked loopback IPv6 address (::1)."
        }

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        // Unique Local Addresses (ULA) fc00::/7
        if ((b0 and 0xFE) == 0xFC) {
            return "Blocked private IPv6 ULA address (fc00::/7)."
        }

        // Link-local fe80::/10
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) {
            return "Blocked IPv6 link-local address (fe80::/10)."
        }

        // Multicast ff00::/8
        if (b0 == 0xFF) {
            return "Blocked IPv6 multicast address."
        }

        // IPv4-mapped IPv6 ::ffff:a.b.c.d
        val isIpv4Mapped = (0..9).all { bytes[it] == 0.toByte() } &&
            bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
        if (isIpv4Mapped) {
            return validateIpv4Bytes(bytes.copyOfRange(12, 16), policy)
        }

        return null
    }

    private fun parseIpLiteralOrNull(host: String): InetAddress? {
        val unbracketed = host.removePrefix("[").removeSuffix("]")
        val isV4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(unbracketed)
        val isV6Literal = unbracketed.contains(':')
        if (!isV4Literal && !isV6Literal) return null
        return runCatching { InetAddress.getByName(unbracketed) }.getOrNull()
    }

    /**
     * OkHttp Dns implementation that validates every resolved address before returning it,
     * defeating DNS rebinding and internal IP resolution at connect time.
     */
    class SafeDns(private val policy: UrlSecurityPolicy) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val clean = hostname.trimEnd('.').lowercase()
            val isLoopbackHostname = clean == "localhost" || clean == "ip6-localhost" ||
                clean == "ip6-loopback" || clean.endsWith(".localhost")
            if (isLoopbackHostname && policy != UrlSecurityPolicy.LOOPBACK_TEST) {
                throw UnknownHostException("Blocked local host '$hostname'")
            }
            if (!isLoopbackHostname && (clean in blockedLocalHostnames || blockedSuffixes.any { clean.endsWith(it) })) {
                throw UnknownHostException("Blocked internal host '$hostname'")
            }

            val resolved = InetAddress.getAllByName(hostname).toList()
            for (addr in resolved) {
                val violation = validateResolvedAddress(addr, policy)
                if (violation != null) {
                    throw UnknownHostException("SSRF protection: $violation")
                }
            }
            return resolved
        }
    }

    fun createSafeHttpClient(
        policy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
        connectTimeoutSec: Long = 12L,
        readTimeoutSec: Long = 15L,
        callTimeoutSec: Long = 25L,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SafeDns(policy))
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "AasmoLoader-Android/2.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /**
     * Executes an HTTP request following redirects manually up to [MAX_REDIRECT_HOPS].
     * Every redirect hop is validated against [policy], loops are rejected, and sensitive
     * headers (Authorization / Cookie) are never forwarded across origins.
     */
    fun executeWithSafeRedirects(
        client: OkHttpClient,
        initialRequest: Request,
        policy: UrlSecurityPolicy = UrlSecurityPolicy.STRICT,
        maxHops: Int = MAX_REDIRECT_HOPS,
    ): Response {
        val initialValidation = validateSyntaxAndHost(initialRequest.url.toString(), policy)
        if (initialValidation is UrlValidationResult.Rejected) {
            throw SecurityException(initialValidation.reason)
        }

        val visitedUrls = linkedSetOf(initialRequest.url.toString())
        var currentReq = initialRequest
        var hopCount = 0

        while (true) {
            val response = client.newCall(currentReq).execute()
            if (!isRedirectCode(response.code)) {
                return response
            }

            val location = response.header("Location")?.trim()
            val prevUrl = currentReq.url
            response.close()

            if (location.isNullOrBlank()) {
                throw IOException("HTTP redirect (${response.code}) missing Location header.")
            }

            if (hopCount >= maxHops) {
                throw IOException("Exceeded maximum safe redirect limit ($maxHops hops).")
            }

            val nextUrl = prevUrl.resolve(location)
                ?: throw SecurityException("Malformed redirect target '$location'.")

            val nextUrlString = nextUrl.toString()
            if (!visitedUrls.add(nextUrlString)) {
                throw IOException("Redirect loop detected at '$nextUrlString'.")
            }

            val hopCheck = validateSyntaxAndHost(nextUrlString, policy)
            if (hopCheck is UrlValidationResult.Rejected) {
                throw SecurityException("Unsafe redirect target blocked: ${hopCheck.reason}")
            }

            val sameOrigin = prevUrl.scheme == nextUrl.scheme &&
                prevUrl.host.equals(nextUrl.host, ignoreCase = true) &&
                prevUrl.port == nextUrl.port

            val nextMethod = when (response.code) {
                307, 308 -> currentReq.method
                else -> if (currentReq.method.equals("HEAD", ignoreCase = true)) "HEAD" else "GET"
            }
            val nextBody = if (nextMethod == currentReq.method) currentReq.body else null

            val nextBuilder = currentReq.newBuilder()
                .url(nextUrl)
                .method(nextMethod, nextBody)

            if (!sameOrigin) {
                nextBuilder.removeHeader("Authorization")
                nextBuilder.removeHeader("Cookie")
                nextBuilder.removeHeader("Proxy-Authorization")
            }

            currentReq = nextBuilder.build()
            hopCount++
        }
    }

    private fun isRedirectCode(code: Int): Boolean {
        return code == 300 || code == 301 || code == 302 || code == 303 || code == 307 || code == 308
    }
}
