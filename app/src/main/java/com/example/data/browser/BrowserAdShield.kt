package com.example.data.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.local.FilterRuleEntity
import com.example.data.local.SiteRuleEntity
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

enum class SafetyWarningLevel {
    SAFE_HTTPS,
    HTTP_UNENCRYPTED,
    CERT_ERROR,
    SUSPICIOUS_URL,
}

data class UrlSafetyReport(
    val level: SafetyWarningLevel,
    val summary: String,
    val detail: String,
)

object BrowserAdShield {
    private val builtInAdHosts = hashSetOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "ads.yahoo.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "moatads.com",
        "adsrvr.org",
        "amazon-adsystem.com",
    )

    private val builtInTrackerHosts = hashSetOf(
        "scorecardresearch.com",
        "quantserve.com",
        "analytics.tiktok.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "branch.io",
        "appsflyer.com",
        "fullstory.com",
        "clarity.ms",
        "mc.yandex.ru",
    )

    private val customBlockPatterns = ConcurrentHashMap.newKeySet<String>()
    private val customAllowPatterns = ConcurrentHashMap.newKeySet<String>()
    private val siteRuleCache = ConcurrentHashMap<String, SiteRuleEntity>()

    fun updateCustomRules(rules: List<FilterRuleEntity>) {
        customBlockPatterns.clear()
        customAllowPatterns.clear()
        rules.filter { it.enabled }.forEach { r ->
            val p = r.pattern.trim().lowercase()
            if (p.isNotBlank()) {
                if (r.isAllowRule) customAllowPatterns.add(p) else customBlockPatterns.add(p)
            }
        }
    }

    fun updateSiteRules(rules: List<SiteRuleEntity>) {
        siteRuleCache.clear()
        rules.forEach { siteRuleCache[it.host.lowercase()] = it }
    }

    fun getSiteRule(pageUrl: String?): SiteRuleEntity? {
        val host = extractHost(pageUrl) ?: return null
        return siteRuleCache[host]
    }

    fun shouldBlock(
        requestUrl: String?,
        pageUrl: String? = null,
        blockAds: Boolean = true,
        blockThirdPartyTrackers: Boolean = true,
    ): Boolean {
        if (requestUrl.isNullOrBlank()) return false
        val reqHost = extractHost(requestUrl) ?: return false
        val pageHost = extractHost(pageUrl)

        // Check per-site allowlist first
        if (pageHost != null) {
            val siteRule = siteRuleCache[pageHost]
            if (siteRule != null && (!siteRule.adShieldEnabled || siteRule.allowListed)) {
                return false
            }
        }

        if (customAllowPatterns.any { reqHost == it || reqHost.endsWith(".$it") }) {
            return false
        }
        if (customBlockPatterns.any { reqHost == it || reqHost.endsWith(".$it") || requestUrl.lowercase().contains(it) }) {
            return true
        }

        if (blockAds && builtInAdHosts.any { reqHost == it || reqHost.endsWith(".$it") }) {
            return true
        }

        val isThirdParty = pageHost == null || (reqHost != pageHost && !reqHost.endsWith(".$pageHost"))
        if (blockThirdPartyTrackers && isThirdParty && builtInTrackerHosts.any { reqHost == it || reqHost.endsWith(".$it") }) {
            return true
        }

        return false
    }

    fun inspectUrlSafety(url: String?, sslErrorMessage: String? = null): UrlSafetyReport {
        if (!sslErrorMessage.isNullOrBlank()) {
            return UrlSafetyReport(
                level = SafetyWarningLevel.CERT_ERROR,
                summary = "TLS Certificate Warning",
                detail = sslErrorMessage,
            )
        }
        if (url.isNullOrBlank()) {
            return UrlSafetyReport(
                level = SafetyWarningLevel.SAFE_HTTPS,
                summary = "New Tab",
                detail = "No remote webpage loaded.",
            )
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase().orEmpty()
        val host = uri?.host?.lowercase().orEmpty()

        val hasPunycode = host.contains("xn--")
        val hasUserInfoAt = url.substringBefore('/').substringAfter("://", "").contains("@")
        val isIpLiteral = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)

        if (hasPunycode || hasUserInfoAt || (scheme == "http" && isIpLiteral)) {
            return UrlSafetyReport(
                level = SafetyWarningLevel.SUSPICIOUS_URL,
                summary = "Suspicious Address Pattern",
                detail = "URL contains punycode/embedded credentials or raw IP routing. Verify domain carefully before entering credentials.",
            )
        }

        return if (scheme == "https") {
            UrlSafetyReport(
                level = SafetyWarningLevel.SAFE_HTTPS,
                summary = "HTTPS Encrypted Transport",
                detail = "Connection uses TLS encryption. Note: HTTPS protects data in transit, not site trustworthiness.",
            )
        } else {
            UrlSafetyReport(
                level = SafetyWarningLevel.HTTP_UNENCRYPTED,
                summary = "Unencrypted HTTP Connection",
                detail = "Avoid entering passwords or sensitive information on HTTP pages.",
            )
        }
    }

    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { Uri.parse(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
}
