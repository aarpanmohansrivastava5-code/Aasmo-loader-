package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SearchEngine
import com.example.data.browser.BrowserAdShield
import com.example.data.browser.SafetyWarningLevel
import com.example.data.files.AasmoFileManager
import com.example.data.files.FileCategory
import com.example.data.files.FileSortMode
import com.example.data.local.FilterRuleEntity
import com.example.data.local.SiteRuleEntity
import com.example.data.media.MediaKind
import com.example.data.media.PlatformDetector
import com.example.data.media.SupportedPlatform
import com.example.ui.theme.AccentPreset
import com.example.viewmodel.BrowserViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aasmo Loader", appName)
  }

  @Test
  fun `browser viewmodel routes urls, search engines, tab groups, and restores closed tabs`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = BrowserViewModel(app)

    vm.navigateToInput("github.com")
    assertEquals("https://github.com", vm.uiState.value.activeTab.url)

    vm.newTab(isPrivate = false, groupName = "Research")
    vm.navigateToInput("kotlin coroutines", SearchEngine.DUCKDUCKGO)
    assertTrue(vm.uiState.value.activeTab.url.startsWith("https://duckduckgo.com/?q="))
    assertEquals("Research", vm.uiState.value.activeTab.groupName)
    assertEquals(2, vm.uiState.value.tabs.size)

    vm.closeTab(1)
    assertEquals(1, vm.uiState.value.tabs.size)
    assertEquals(1, vm.uiState.value.recentlyClosedCount)

    vm.restoreLastClosedTab()
    assertEquals(2, vm.uiState.value.tabs.size)
    assertEquals("https://duckduckgo.com/?q=kotlin+coroutines", vm.uiState.value.activeTab.url)
  }

  @Test
  fun `browser viewmodel manages private tabs and background media indicators`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = BrowserViewModel(app)

    val firstTabId = vm.uiState.value.activeTab.id
    vm.navigateToInput("youtube.com")
    vm.onTabMediaStateChanged(firstTabId, hasMedia = true, isPlaying = true, mediaTitle = "Lo-Fi Mix")

    vm.newTab(isPrivate = true)
    assertEquals(2, vm.uiState.value.tabs.size)
    assertTrue(vm.uiState.value.activeTab.isPrivate)

    val bgTab = vm.uiState.value.tabs.first { it.id == firstTabId }
    assertTrue(bgTab.hasMedia)
    assertTrue(bgTab.isPlayingMedia)
    assertEquals("Lo-Fi Mix", bgTab.mediaTitle)
  }

  @Test
  fun `ad shield enforces built-in rules, custom rules, per-site rules, and url safety`() {
    BrowserAdShield.updateCustomRules(emptyList())
    BrowserAdShield.updateSiteRules(emptyList())

    assertTrue(BrowserAdShield.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "https://example.com"))
    assertTrue(BrowserAdShield.shouldBlock("https://pixel.facebook.com/tr", "https://example.com"))
    assertFalse(BrowserAdShield.shouldBlock("https://github.com/google/material-design-icons", "https://github.com"))

    // Custom block + allow rules
    BrowserAdShield.updateCustomRules(
      listOf(
        FilterRuleEntity(pattern = "custom-tracker.net", isAllowRule = false),
        FilterRuleEntity(pattern = "pixel.facebook.com", isAllowRule = true),
      )
    )
    assertTrue(BrowserAdShield.shouldBlock("https://cdn.custom-tracker.net/beacon.js", "https://example.com"))
    assertFalse(BrowserAdShield.shouldBlock("https://pixel.facebook.com/tr", "https://example.com"))

    // Per-site allowlist bypass
    BrowserAdShield.updateSiteRules(listOf(SiteRuleEntity(host = "trusted.org", allowListed = true)))
    assertFalse(BrowserAdShield.shouldBlock("https://cdn.custom-tracker.net/beacon.js", "https://trusted.org/page"))

    // Cleanup global object state
    BrowserAdShield.updateCustomRules(emptyList())
    BrowserAdShield.updateSiteRules(emptyList())

    // URL safety classification
    assertEquals(SafetyWarningLevel.SAFE_HTTPS, BrowserAdShield.inspectUrlSafety("https://github.com").level)
    assertEquals(SafetyWarningLevel.HTTP_UNENCRYPTED, BrowserAdShield.inspectUrlSafety("http://example.com").level)
    assertEquals(SafetyWarningLevel.SUSPICIOUS_URL, BrowserAdShield.inspectUrlSafety("https://xn--pple-43d.com/login").level)
    assertEquals(SafetyWarningLevel.CERT_ERROR, BrowserAdShield.inspectUrlSafety("https://example.com", "Self-signed certificate").level)
  }

  @Test
  fun `platform detector classifies urls, handles, direct media, and rejects unsafe schemes`() {
    val ytPost = PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ", resolveRedirects = false)
    assertEquals(SupportedPlatform.YOUTUBE, ytPost.platform)
    assertEquals(MediaKind.POST, ytPost.initialClassification)

    val directVideo = PlatformDetector.detect("https://cdn.example.org/clips/sample_1080p.mp4", resolveRedirects = false)
    assertEquals(SupportedPlatform.DIRECT_MEDIA, directVideo.platform)
    assertEquals(MediaKind.DIRECT_MEDIA, directVideo.initialClassification)

    val handleTarget = PlatformDetector.detect("@nasa", preferredPlatform = SupportedPlatform.REDDIT, resolveRedirects = false)
    assertTrue(handleTarget.isHandleOnly)
    assertEquals(SupportedPlatform.REDDIT, handleTarget.platform)
    assertEquals("https://www.reddit.com/user/nasa", handleTarget.resolvedUrl)
    assertEquals(MediaKind.PROFILE, handleTarget.initialClassification)

    var threw = false
    try {
      PlatformDetector.detect("javascript:alert(1)", resolveRedirects = false)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }

  @Test
  fun `file manager creates folders, saves reader articles, searches, renames, and deletes`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val fm = AasmoFileManager(app)

    val folderRes = fm.createFolder(null, "UnitTestFolder")
    assertTrue(folderRes.isSuccess)
    val folder = folderRes.getOrThrow()

    val docRes = fm.saveReaderDocument("Test Article", "Body paragraph for offline reader.")
    assertTrue(docRes.isSuccess)
    val docFile = docRes.getOrThrow()

    val found = fm.listFiles(category = FileCategory.DOCUMENTS, sortMode = FileSortMode.NAME_ASC, query = "Test Article")
    assertTrue(found.any { it.path == docFile.absolutePath })

    val renamedRes = fm.renameFile(docFile.absolutePath, "RenamedArticle.txt")
    assertTrue(renamedRes.isSuccess)
    val renamed = renamedRes.getOrThrow()
    assertTrue(renamed.exists())

    assertTrue(fm.deletePath(renamed.absolutePath))
    assertTrue(fm.deletePath(folder.absolutePath))
  }

  @Test
  fun `accent preset resolves defaults cleanly`() {
    assertEquals(AccentPreset.NEON_CYAN, AccentPreset.fromId("cyan"))
    assertEquals(AccentPreset.PRISM_INDIGO, AccentPreset.fromId("unknown"))
  }
}
