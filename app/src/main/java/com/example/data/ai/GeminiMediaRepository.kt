package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiMediaRepo"

data class MediaInsight(
    val title: String,
    val category: String,
    val summary: String,
    val suggestedFileName: String,
    val recommendedFormat: String,
    val tags: List<String>,
    val safetyNote: String,
)

data class ExtractedLinkItem(
    val url: String,
    val label: String,
    val fileType: String,
)

class GeminiMediaRepository {
    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Aasmo Intelligence, a concise, technical AI assistant inside the Aasmo Loader Android app. " +
                        "Help users analyze URLs, GitHub repositories, media downloads, file formats, screenshots, privacy controls, and web articles. " +
                        "Keep responses crisp, structured, and directly actionable."
                )
            },
        )
    }

    private val insightSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short descriptive title of the analyzed link or media"),
                "category" to Schema.string(description = "Category such as Video, Source Archive, Document, Web Page, or Release Binary"),
                "summary" to Schema.string(description = "Concise 2-3 sentence summary of what this content is and how best to use/download it"),
                "suggestedFileName" to Schema.string(description = "Clean filename without spaces"),
                "recommendedFormat" to Schema.string(description = "Best format or container, e.g., MP4 1080p, ZIP Archive, APK, PDF"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 5 smart organization tags",
                ),
                "safetyNote" to Schema.string(description = "Brief security or verification recommendation"),
            ),
        )
    }

    private val linkExtractionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "links" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "url" to Schema.string(description = "Full HTTP or HTTPS URL"),
                            "label" to Schema.string(description = "Short descriptive label or filename"),
                            "fileType" to Schema.string(description = "Likely type e.g. ZIP, APK, MP4, PDF, Page"),
                        )
                    ),
                    description = "All valid HTTP/HTTPS URLs extracted from the input text",
                )
            )
        )
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = insightSchema
            },
        )
    }

    private val linkExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = linkExtractionSchema
            },
        )
    }

    fun streamPrompt(
        prompt: String,
        historyPairs: List<Pair<Boolean, String>>,
    ): Flow<String> = flow {
        try {
            val chatHistory = historyPairs.map { (isUser, msg) ->
                content(if (isUser) "user" else "model") { text(msg) }
            }
            val chat = chatModel.startChat(history = chatHistory)
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                chat.sendMessageStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini stream timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini stream failed", e)
            throw e
        }
    }

    fun analyzeImage(bitmap: Bitmap, prompt: String): Flow<String> = flow {
        try {
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                chatModel.generateContentStream(
                    content {
                        image(bitmap)
                        text(prompt.ifBlank { "Analyze this image, identify any text, URLs, or code, and summarize clearly." })
                    }
                ).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty vision response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Multimodal vision timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multimodal vision failed", e)
            throw e
        }
    }

    fun summarizeArticle(title: String, articleText: String): Flow<String> = flow {
        try {
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                chatModel.generateContentStream(
                    "Provide a crisp TL;DR bullet summary (max 5 bullet points) of this webpage article.\nTitle: $title\n\nContent:\n${articleText.take(9000)}"
                ).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty summary.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Article TL;DR timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Article TL;DR failed", e)
            throw e
        }
    }

    suspend fun extractLinksFromText(rawText: String): List<ExtractedLinkItem> {
        try {
            val response = withTimeout(60_000L) {
                linkExtractorModel.generateContent(
                    "Extract every valid downloadable or web URL from the text below.\n\n$rawText"
                )
            }
            val jsonString = response.text ?: throw IllegalStateException("Empty link extraction response.")
            val json = JSONObject(jsonString)
            val arr = json.optJSONArray("links") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val url = obj.optString("url").trim()
                    if (url.startsWith("http")) {
                        add(
                            ExtractedLinkItem(
                                url = url,
                                label = obj.optString("label", url.substringAfterLast('/')).ifBlank { url },
                                fileType = obj.optString("fileType", "Link"),
                            )
                        )
                    }
                }
            }.distinctBy { it.url }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Link extraction timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Link extraction failed", e)
            throw e
        }
    }

    suspend fun analyzeTarget(target: String, extraContext: String = ""): MediaInsight {
        try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(
                    "Analyze this URL, repository, or media item for Aasmo Loader.\n" +
                        "Target: $target\n" +
                        if (extraContext.isNotBlank()) "Context: $extraContext\n" else ""
                )
            }
            val jsonString = response.text ?: throw IllegalStateException("Empty structured response from Gemini.")
            val json = JSONObject(jsonString)
            val tagsArr = json.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        add(tagsArr.optString(i))
                    }
                }
            }.filter { it.isNotBlank() }

            return MediaInsight(
                title = json.optString("title", target.take(40)),
                category = json.optString("category", "Media"),
                summary = json.optString("summary", "Analysis complete."),
                suggestedFileName = json.optString("suggestedFileName", "aasmo_asset"),
                recommendedFormat = json.optString("recommendedFormat", "Auto"),
                tags = tags,
                safetyNote = json.optString("safetyNote", "Verify source authenticity before opening."),
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured analysis timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured analysis failed", e)
            throw e
        }
    }
}
