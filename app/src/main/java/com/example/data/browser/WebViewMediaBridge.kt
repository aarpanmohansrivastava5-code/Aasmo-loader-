package com.example.data.browser

import android.webkit.JavascriptInterface
import org.json.JSONArray

data class DetectedPageMedia(
    val url: String,
    val title: String,
    val mediaType: String, // VIDEO, AUDIO, IMAGE
)

class WebViewJsBridge(
    private val tabId: Long,
    private val onMediaUpdate: (tabId: Long, hasMedia: Boolean, isPlaying: Boolean, title: String) -> Unit,
    private val onSniffedMedia: (tabId: Long, items: List<DetectedPageMedia>) -> Unit,
    private val onReaderExtracted: (tabId: Long, title: String, text: String) -> Unit,
) {
    @JavascriptInterface
    fun postMediaState(hasMedia: Boolean, isPlaying: Boolean, title: String) {
        onMediaUpdate(tabId, hasMedia, isPlaying, title)
    }

    @JavascriptInterface
    fun postSniffedMedia(jsonStr: String) {
        val list = mutableListOf<DetectedPageMedia>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url")
                if (url.startsWith("http")) {
                    list.add(
                        DetectedPageMedia(
                            url = url,
                            title = obj.optString("title", "Media Asset"),
                            mediaType = obj.optString("type", "MEDIA"),
                        )
                    )
                }
            }
        }
        onSniffedMedia(tabId, list.distinctBy { it.url })
    }

    @JavascriptInterface
    fun postReaderText(title: String, bodyText: String) {
        onReaderExtracted(tabId, title, bodyText)
    }
}

object WebViewScripts {
    val installMediaObserverJs: String = """
        (function() {
          if (window.__aasmoMediaHooked) {
            if (window.__aasmoReportMedia) window.__aasmoReportMedia();
            return;
          }
          window.__aasmoMediaHooked = true;

          function getMediaTitle() {
            try {
              if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title) {
                return navigator.mediaSession.metadata.title;
              }
            } catch (e) {}
            return document.title || location.hostname || 'Playing Media';
          }

          window.__aasmoReportMedia = function() {
            try {
              var els = Array.prototype.slice.call(document.querySelectorAll('video, audio'));
              var hasMedia = els.length > 0;
              var isPlaying = els.some(function(el) { return !el.paused && !el.ended && el.readyState >= 2; });
              if (window.AasmoBridge) {
                window.AasmoBridge.postMediaState(hasMedia, isPlaying, getMediaTitle());
              }
            } catch (e) {}
          };

          function attachListeners() {
            var els = document.querySelectorAll('video, audio');
            els.forEach(function(el) {
              if (el.__aasmoBound) return;
              el.__aasmoBound = true;
              ['play', 'playing', 'pause', 'ended', 'loadedmetadata'].forEach(function(ev) {
                el.addEventListener(ev, window.__aasmoReportMedia, true);
              });
            });
            window.__aasmoReportMedia();
          }

          attachListeners();
          setInterval(attachListeners, 1500);
        })();
    """.trimIndent()

    fun togglePlaybackJs(): String = """
        (function() {
          var els = Array.prototype.slice.call(document.querySelectorAll('video, audio'));
          if (!els.length) return;
          var anyPlaying = els.some(function(el) { return !el.paused && !el.ended; });
          els.forEach(function(el) {
            try {
              if (anyPlaying) { el.pause(); } else { el.play(); }
            } catch (e) {}
          });
          if (window.__aasmoReportMedia) setTimeout(window.__aasmoReportMedia, 120);
        })();
    """.trimIndent()

    fun setPlaybackJs(play: Boolean): String = """
        (function() {
          var els = Array.prototype.slice.call(document.querySelectorAll('video, audio'));
          els.forEach(function(el) {
            try {
              if ($play) { el.play(); } else { el.pause(); }
            } catch (e) {}
          });
          if (window.__aasmoReportMedia) setTimeout(window.__aasmoReportMedia, 120);
        })();
    """.trimIndent()

    fun seekByJs(seconds: Int): String = """
        (function() {
          var els = Array.prototype.slice.call(document.querySelectorAll('video, audio'));
          els.forEach(function(el) {
            try {
              if (isFinite(el.currentTime)) {
                el.currentTime = Math.max(0, el.currentTime + ($seconds));
              }
            } catch (e) {}
          });
        })();
    """.trimIndent()

    val sniffMediaJs: String = """
        (function() {
          var out = [];
          var seen = {};
          function pushItem(url, title, type) {
            if (!url || !url.startsWith('http') || seen[url]) return;
            seen[url] = true;
            out.push({url: url, title: title || document.title || 'Media', type: type});
          }
          document.querySelectorAll('video').forEach(function(v, i) {
            pushItem(v.currentSrc || v.src, (document.title || 'Video') + ' #' + (i+1), 'VIDEO');
            v.querySelectorAll('source').forEach(function(s) { pushItem(s.src, document.title || 'Video', 'VIDEO'); });
          });
          document.querySelectorAll('audio').forEach(function(a, i) {
            pushItem(a.currentSrc || a.src, (document.title || 'Audio') + ' #' + (i+1), 'AUDIO');
            a.querySelectorAll('source').forEach(function(s) { pushItem(s.src, document.title || 'Audio', 'AUDIO'); });
          });
          document.querySelectorAll('a[href]').forEach(function(a) {
            if (/\.(mp4|webm|m3u8|mp3|wav|ogg|pdf|zip|apk)(\?|$)/i.test(a.href)) {
              pushItem(a.href, a.textContent.trim() || a.href.split('/').pop(), 'MEDIA');
            }
          });
          document.querySelectorAll('img[src]').forEach(function(img, i) {
            if ((img.naturalWidth || 0) >= 240 && (img.naturalHeight || 0) >= 180) {
              pushItem(img.src, img.alt || ('Image #' + (i+1)), 'IMAGE');
            }
          });
          if (window.AasmoBridge) window.AasmoBridge.postSniffedMedia(JSON.stringify(out.slice(0, 30)));
        })();
    """.trimIndent()

    val extractReaderJs: String = """
        (function() {
          var article = document.querySelector('article, main, [role="main"]') || document.body;
          var text = (article ? article.innerText : '') || '';
          text = text.replace(/\n{3,}/g, '\n\n').trim().slice(0, 12000);
          if (window.AasmoBridge) window.AasmoBridge.postReaderText(document.title || location.hostname, text);
        })();
    """.trimIndent()
}
