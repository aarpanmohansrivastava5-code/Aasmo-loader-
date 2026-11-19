package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubOwner(
    val login: String = "",
    @Json(name = "avatar_url") val avatarUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubLicense(
    val key: String = "",
    val name: String = "",
    @Json(name = "spdx_id") val spdxId: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long = 0L,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val description: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "stargazers_count") val stars: Int = 0,
    @Json(name = "forks_count") val forks: Int = 0,
    @Json(name = "open_issues_count") val openIssues: Int = 0,
    val language: String? = null,
    val license: GitHubLicense? = null,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "updated_at") val updatedAt: String = "",
    val owner: GitHubOwner = GitHubOwner(),
) {
    fun archiveZipUrl(branch: String = defaultBranch): String =
        "https://github.com/$fullName/archive/refs/heads/$branch.zip"
}

@JsonClass(generateAdapter = true)
data class GitHubSearchResponse(
    val items: List<GitHubRepo> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    val id: Long = 0L,
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "download_count") val downloadCount: Int = 0,
    @Json(name = "content_type") val contentType: String = "application/octet-stream",
    @Json(name = "browser_download_url") val downloadUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    val id: Long = 0L,
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "zipball_url") val zipballUrl: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubContentNode(
    val name: String = "",
    val path: String = "",
    val type: String = "file",
    val size: Long = 0L,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubReadme(
    val name: String = "README.md",
    val content: String = "",
    val encoding: String = "base64",
)

@JsonClass(generateAdapter = true)
data class GitHubGistFile(
    val filename: String = "",
    val language: String? = null,
    @Json(name = "raw_url") val rawUrl: String = "",
    val size: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class GitHubGist(
    val id: String = "",
    val description: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    val files: Map<String, GitHubGistFile> = emptyMap(),
)

data class GitHubRateLimitStatus(
    val remaining: Int = -1,
    val limit: Int = -1,
    val isExhausted: Boolean = false,
)

interface GitHubApiService {
    @GET("users/{username}/repos")
    suspend fun getUserRepos(
        @Path("username") username: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Header("Authorization") authHeader: String? = null,
    ): List<GitHubRepo>

    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Header("Authorization") authHeader: String? = null,
    ): GitHubSearchResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authHeader: String? = null,
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getRepoBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Header("Authorization") authHeader: String? = null,
    ): List<GitHubBranch>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getRepoReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 15,
        @Header("Authorization") authHeader: String? = null,
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null,
        @Header("Authorization") authHeader: String? = null,
    ): List<GitHubContentNode>

    @GET("repos/{owner}/{repo}/readme")
    suspend fun getRepoReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null,
        @Header("Authorization") authHeader: String? = null,
    ): GitHubReadme

    @GET("users/{username}/gists")
    suspend fun getUserGists(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 15,
        @Header("Authorization") authHeader: String? = null,
    ): List<GitHubGist>
}

object NetworkModule {
    private val _gitHubRateLimit = MutableStateFlow(GitHubRateLimitStatus())
    val gitHubRateLimit = _gitHubRateLimit.asStateFlow()

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AasmoLoader-Android/2.0")
                    .build()
                val resp = chain.proceed(req)
                if (req.url.host.contains("api.github.com")) {
                    val rem = resp.header("X-RateLimit-Remaining")?.toIntOrNull()
                    val lim = resp.header("X-RateLimit-Limit")?.toIntOrNull()
                    if (rem != null && lim != null) {
                        _gitHubRateLimit.value = GitHubRateLimitStatus(
                            remaining = rem,
                            limit = lim,
                            isExhausted = rem == 0,
                        )
                    }
                }
                resp
            }
            .addInterceptor(logging)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val gitHubApi: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }
}
