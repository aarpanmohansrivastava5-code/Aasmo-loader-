package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aasmo_settings")

enum class WallpaperMode(val id: String, val label: String) {
    PRISM_ORBS("orbs", "Prism Orbs"),
    WALL_PRISM("prism", "Nebula Glass"),
    WALL_AURORA("aurora", "Cyber Aurora"),
    CUSTOM_PHOTO("custom", "Custom Photo");

    companion object {
        fun fromId(id: String?): WallpaperMode = entries.firstOrNull { it.id == id } ?: PRISM_ORBS
    }
}

enum class SearchEngine(val id: String, val label: String, val searchUrlTemplate: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s");

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

enum class DuplicateBehavior(val id: String, val label: String) {
    RENAME("rename", "Auto-rename"),
    SKIP("skip", "Skip duplicate"),
    REPLACE("replace", "Replace file");

    companion object {
        fun fromId(id: String?): DuplicateBehavior = entries.firstOrNull { it.id == id } ?: RENAME
    }
}

enum class ReaderTheme(val id: String, val label: String, val bgHex: Long, val textHex: Long) {
    PRISM_DARK("prism_dark", "Prism Dark", 0xFF141627, 0xFFE8ECF8),
    SEPIA("sepia", "Warm Sepia", 0xFF2B231A, 0xFFF3E7D3),
    PURE_OLED("oled", "Pure OLED", 0xFF000000, 0xFFDCE2EE),
    SOFT_LIGHT("light", "Paper Light", 0xFFF6F4EE, 0xFF1F2330);

    companion object {
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.id == id } ?: PRISM_DARK
    }
}

data class UserSettings(
    // Customization
    val accentPreset: AccentPreset = AccentPreset.PRISM_INDIGO,
    val wallpaperMode: WallpaperMode = WallpaperMode.PRISM_ORBS,
    val customWallpaperUri: String? = null,
    val glassIntensity: Float = 0.85f,
    // Browser
    val homepageUrl: String = "",
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val desktopUserAgent: Boolean = false,
    val readerFontSizeSp: Int = 15,
    val readerLineHeightMultiplier: Float = 1.55f,
    val readerTheme: ReaderTheme = ReaderTheme.PRISM_DARK,
    // Downloads
    val defaultChunkCount: Int = 8,
    val maxConcurrentDownloads: Int = 2,
    val wifiOnlyDownloads: Boolean = false,
    val askBeforeDownload: Boolean = false,
    val duplicateBehavior: DuplicateBehavior = DuplicateBehavior.RENAME,
    val autoRetryDownloads: Boolean = true,
    val downloadFolderLabel: String = "App Storage + Downloads/AasmoLoader",
    val downloadSafTreeUri: String? = null,
    val notificationsEnabled: Boolean = true,
    // Privacy & Security
    val adBlockerEnabled: Boolean = true,
    val blockThirdPartyTrackers: Boolean = true,
    val unsafeSiteProtection: Boolean = true,
    val credentialManagerEnabled: Boolean = true,
    val autofillEnabled: Boolean = true,
    // GitHub & Hubs
    val githubToken: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ACCENT_ID = stringPreferencesKey("accent_id")
        val WALLPAPER_MODE = stringPreferencesKey("wallpaper_mode")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")

        val HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        val SEARCH_ENGINE_ID = stringPreferencesKey("search_engine_id")
        val DESKTOP_UA = booleanPreferencesKey("desktop_ua")
        val READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val READER_THEME_ID = stringPreferencesKey("reader_theme_id")

        val DEFAULT_CHUNKS = intPreferencesKey("default_chunks")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val DUPLICATE_BEHAVIOR = stringPreferencesKey("duplicate_behavior")
        val AUTO_RETRY_DOWNLOADS = booleanPreferencesKey("auto_retry_downloads")
        val DOWNLOAD_FOLDER_LABEL = stringPreferencesKey("download_folder_label")
        val DOWNLOAD_SAF_URI = stringPreferencesKey("download_saf_uri")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

        val AD_BLOCKER_ENABLED = booleanPreferencesKey("ad_blocker_enabled")
        val BLOCK_THIRD_PARTY_TRACKERS = booleanPreferencesKey("block_third_party_trackers")
        val UNSAFE_SITE_PROTECTION = booleanPreferencesKey("unsafe_site_protection")
        val CREDENTIAL_MANAGER_ENABLED = booleanPreferencesKey("credential_manager_enabled")
        val AUTOFILL_ENABLED = booleanPreferencesKey("autofill_enabled")

        val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            accentPreset = AccentPreset.fromId(prefs[Keys.ACCENT_ID]),
            wallpaperMode = WallpaperMode.fromId(prefs[Keys.WALLPAPER_MODE]),
            customWallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI],
            glassIntensity = prefs[Keys.GLASS_INTENSITY] ?: 0.85f,
            homepageUrl = prefs[Keys.HOMEPAGE_URL] ?: "",
            searchEngine = SearchEngine.fromId(prefs[Keys.SEARCH_ENGINE_ID]),
            desktopUserAgent = prefs[Keys.DESKTOP_UA] ?: false,
            readerFontSizeSp = prefs[Keys.READER_FONT_SIZE] ?: 15,
            readerLineHeightMultiplier = prefs[Keys.READER_LINE_HEIGHT] ?: 1.55f,
            readerTheme = ReaderTheme.fromId(prefs[Keys.READER_THEME_ID]),
            defaultChunkCount = prefs[Keys.DEFAULT_CHUNKS] ?: 8,
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: false,
            askBeforeDownload = prefs[Keys.ASK_BEFORE_DOWNLOAD] ?: false,
            duplicateBehavior = DuplicateBehavior.fromId(prefs[Keys.DUPLICATE_BEHAVIOR]),
            autoRetryDownloads = prefs[Keys.AUTO_RETRY_DOWNLOADS] ?: true,
            downloadFolderLabel = prefs[Keys.DOWNLOAD_FOLDER_LABEL] ?: "App Storage + Downloads/AasmoLoader",
            downloadSafTreeUri = prefs[Keys.DOWNLOAD_SAF_URI],
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            adBlockerEnabled = prefs[Keys.AD_BLOCKER_ENABLED] ?: true,
            blockThirdPartyTrackers = prefs[Keys.BLOCK_THIRD_PARTY_TRACKERS] ?: true,
            unsafeSiteProtection = prefs[Keys.UNSAFE_SITE_PROTECTION] ?: true,
            credentialManagerEnabled = prefs[Keys.CREDENTIAL_MANAGER_ENABLED] ?: true,
            autofillEnabled = prefs[Keys.AUTOFILL_ENABLED] ?: true,
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
        )
    }

    suspend fun setAccentPreset(preset: AccentPreset) { context.dataStore.edit { it[Keys.ACCENT_ID] = preset.id } }

    suspend fun setWallpaperMode(mode: WallpaperMode, customUri: String? = null) {
        context.dataStore.edit {
            it[Keys.WALLPAPER_MODE] = mode.id
            if (customUri != null) it[Keys.CUSTOM_WALLPAPER_URI] = customUri
        }
    }

    suspend fun setGlassIntensity(value: Float) { context.dataStore.edit { it[Keys.GLASS_INTENSITY] = value.coerceIn(0.35f, 1.0f) } }
    suspend fun setHomepageUrl(url: String) { context.dataStore.edit { it[Keys.HOMEPAGE_URL] = url.trim() } }
    suspend fun setSearchEngine(engine: SearchEngine) { context.dataStore.edit { it[Keys.SEARCH_ENGINE_ID] = engine.id } }
    suspend fun setDesktopUserAgent(enabled: Boolean) { context.dataStore.edit { it[Keys.DESKTOP_UA] = enabled } }
    suspend fun setReaderFontSize(sp: Int) { context.dataStore.edit { it[Keys.READER_FONT_SIZE] = sp.coerceIn(12, 24) } }
    suspend fun setReaderLineHeight(mult: Float) { context.dataStore.edit { it[Keys.READER_LINE_HEIGHT] = mult.coerceIn(1.2f, 2.2f) } }
    suspend fun setReaderTheme(theme: ReaderTheme) { context.dataStore.edit { it[Keys.READER_THEME_ID] = theme.id } }

    suspend fun setDefaultChunkCount(chunks: Int) { context.dataStore.edit { it[Keys.DEFAULT_CHUNKS] = chunks.coerceIn(2, 16) } }
    suspend fun setMaxConcurrentDownloads(max: Int) { context.dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = max.coerceIn(1, 5) } }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) { context.dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled } }
    suspend fun setAskBeforeDownload(enabled: Boolean) { context.dataStore.edit { it[Keys.ASK_BEFORE_DOWNLOAD] = enabled } }
    suspend fun setDuplicateBehavior(behavior: DuplicateBehavior) { context.dataStore.edit { it[Keys.DUPLICATE_BEHAVIOR] = behavior.id } }
    suspend fun setAutoRetryDownloads(enabled: Boolean) { context.dataStore.edit { it[Keys.AUTO_RETRY_DOWNLOADS] = enabled } }
    suspend fun setDownloadFolder(label: String, safUri: String?) {
        context.dataStore.edit {
            it[Keys.DOWNLOAD_FOLDER_LABEL] = label
            if (safUri != null) it[Keys.DOWNLOAD_SAF_URI] = safUri else it.remove(Keys.DOWNLOAD_SAF_URI)
        }
    }
    suspend fun setNotificationsEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled } }

    suspend fun setAdBlockerEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.AD_BLOCKER_ENABLED] = enabled } }
    suspend fun setBlockThirdPartyTrackers(enabled: Boolean) { context.dataStore.edit { it[Keys.BLOCK_THIRD_PARTY_TRACKERS] = enabled } }
    suspend fun setUnsafeSiteProtection(enabled: Boolean) { context.dataStore.edit { it[Keys.UNSAFE_SITE_PROTECTION] = enabled } }
    suspend fun setCredentialManagerEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.CREDENTIAL_MANAGER_ENABLED] = enabled } }
    suspend fun setAutofillEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.AUTOFILL_ENABLED] = enabled } }

    suspend fun setGithubToken(token: String) { context.dataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() } }
}
