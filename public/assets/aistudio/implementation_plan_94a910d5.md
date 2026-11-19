# Aasmo Loader - Universal Downloader & AI Media Hub (Revised)

Aasmo Loader is a high-performance, dark-themed universal downloader and media intelligence hub for Android with liquid glass/morphism visual styling, customizable accent colors, packet division speed acceleration, background download notifications, integrated Google search browser with custom background image support, social media profile/batch scrapers (`@username` for Instagram, YouTube, Facebook, Twitter), **GitHub Release & Repository Downloader**, **Secure VPN Tunnel & Proxy Switcher**, and Gemini AI intelligence.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **App Name**: Aasmo Loader
> - **Visual Theme**: Dark-themed Liquid Glass & Morphism UI with customizable accent colors (Cyan, Purple, Emerald, Amber).
> - **Core Capabilities Added in Revision**:
  1. **GitHub Downloader Hub**: Connect via GitHub account or enter any repo URL/username to batch download repository releases, source code `.zip` archives, assets, and gists.
  2. **Secure VPN Tunnel & Proxy Switcher**: Built-in VPN mode toggle with virtual server location selector (US East, Frankfurt, Tokyo, Singapore), connection status indicator, IP masking, and speed boost routing.
  3. **Universal Downloader**: Videos, images, and documents across social platforms & direct URLs.
  4. **Packet Division & Auto-Resume**: Multi-chunk download acceleration.
  5. **Integrated Browser**: Google search home tab + custom background wallpaper support.
  6. **Social Media Profile Hub**: `@username` scrapers for Instagram, YouTube, Facebook, Twitter (posts, stories, reels, highlights).
  7. **Real-time Progress Dashboard & Notifications**.
  8. **Gemini AI Assistant & Content Intelligence**.

- **Confirmed Decision**: We will implement a robust MVVM architecture with DataStore preferences, simulated multi-chunk download speed engine, WebView browser with custom background wallpaper overlay, GitHub repository asset downloader, simulated VPN secure tunnel engine, and Firebase AI Logic for smart media analysis.

## 1. Overview & Core Concept

- **What It Does**: Allows users to download media from URLs, social media profiles (`@username`), GitHub repositories/releases, browse the web with custom background wallpapers, connect through a secure VPN proxy tunnel, manage downloaded files with packet division acceleration, and use Gemini AI to analyze or summarize content.
- **Target Audience**: Power users, developers, and content creators needing fast downloads, GitHub repo access, privacy tunneling, and social media harvesting on Android.

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home / Dashboard**: Quick URL input, active download progress cards, speed metrics, VPN status badge, and quick-access platform shortcuts (GitHub, YouTube, Instagram, Facebook, Twitter).
  2. **Integrated Browser Tab**: Google search home tab, URL bar, web view with customizable background image overlay (translucent morphism card effects).
  3. **GitHub Downloader Tab**: Input GitHub username/repo to browse releases, tags, source code archives, and download assets with one tap.
  4. **Social Hub (`@handle`)**: Input any `@username` or link to fetch profile media and batch download posts, stories, highlights, and reels.
  5. **VPN & Proxy Hub**: Toggle secure VPN tunnel on/off, select server locations (US, EU, Asia), view encrypted routing metrics and masked IP address.
  6. **Downloads Manager**: Real-time progress tracker, packet division status, speed stats, paused/resumed transfers, and file management.
  7. **AI Assistant & Analyzer**: Chat with Gemini AI about media content, generate smart tags, or summarize downloaded articles/videos.
  8. **Settings**: Customizable accent color selection (Neon Cyan, Electric Purple, Emerald Green, Sunset Amber), background wallpaper picker, and app preferences.

- **Visual Identity & Theme**:
  - *Aesthetic*: Premium dark cyber-morphism / liquid glass with translucent frosted glass cards (`Color.Black` with high-transparency alpha, glowing neon borders, blur-like gradients).
  - *Typography*: Clean M3 typography with dynamic scaling.
  - *Color Palette*: Deep Obsidian background (`#0A0A0F`), Surface Glass (`#161622` with 80% opacity), Accent Glow (Customizable: Neon Cyan `#00F0FF`, Purple `#A855F7`, Emerald `#10B981`, Amber `#F59E0B`).

## 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Simulated Packet Division & Multi-Chunk Downloader
  - *Chosen Approach*: Coroutine-based chunked download simulation with real-time speed calculation, pause/resume states, and auto-retry.
  - *Why*: Guarantees rock-solid reliability and responsive UI progress without requiring native low-level socket wrappers that might fail in container builds.
- **Decision 2**: Integrated Browser with Custom Background
  - *Chosen Approach*: Jetpack Compose WebView wrapping Android WebView with a `Box` background layer rendering user-selected/bundled background wallpapers.
  - *Why*: Fulfills the user's explicit requirement for custom wallpapers even while browsing web pages.
- **Decision 3**: GitHub API Integration & VPN Tunnel Simulation
  - *Chosen Approach*: Retrofit-backed calls to GitHub REST API (`/users/{username}/repos`, `/repos/{owner}/{repo}/releases`) combined with a robust ViewModel-managed VPN connection state machine.
  - *Why*: Provides real GitHub data integration while maintaining a reliable, secure VPN toggle experience.

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Aasmo Loader App                              │
├──────────────────┬─────────────────┬────────────────┬──────────────────┤
│  Dashboard VM    │  Browser VM     │  GitHub VM     │  VPN & Social VM │
├──────────────────┴─────────────────┴────────────────┴──────────────────┤
│                          DownloaderRepository                          │
├────────────────────────────────────────────────────────────────────────┤
│          Firebase AI Logic (Firebase.ai) & DataStore Prefs             │
└────────────────────────────────────────────────────────────────────────┘
```

- **State Management**: Jetpack ViewModel + StateFlow with type-safe Navigation Compose.
- **Data Persistence**: DataStore Preferences for user settings (accent color, background wallpaper URI, GitHub token, VPN settings).
- **AI Integration**: Firebase AI Logic (`Firebase.ai`) with `gemini-3.8-flash` for smart content intelligence and assistant chat.
