package com.example.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.data.network.GitHubBranch
import com.example.data.network.GitHubContentNode
import com.example.data.network.GitHubGist
import com.example.data.network.GitHubRateLimitStatus
import com.example.data.network.GitHubRelease
import com.example.data.network.GitHubRepo
import com.example.data.network.NetworkModule
import com.example.data.social.ExtractedSocialMedia
import com.example.data.social.SocialMediaCategory
import com.example.data.social.SocialPlatform
import com.example.data.social.SocialProfileExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface GitHubUiState {
    data object Idle : GitHubUiState
    data object Loading : GitHubUiState
    data class Loaded(
        val repos: List<GitHubRepo>,
        val gists: List<GitHubGist> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val currentQuery: String = "",
    ) : GitHubUiState
    data class Error(val message: String) : GitHubUiState
}

sealed interface RepoDetailSheetState {
    data object Idle : RepoDetailSheetState
    data object Loading : RepoDetailSheetState
    data class Loaded(
        val repo: GitHubRepo,
        val branches: List<GitHubBranch>,
        val selectedBranch: String,
        val releases: List<GitHubRelease>,
        val currentPath: String,
        val treeNodes: List<GitHubContentNode>,
        val readmeMarkdown: String,
    ) : RepoDetailSheetState
    data class Error(val message: String) : RepoDetailSheetState
}

sealed interface SocialUiState {
    data object Idle : SocialUiState
    data object Loading : SocialUiState
    data class Loaded(val items: List<ExtractedSocialMedia>) : SocialUiState
    data class Error(val message: String) : SocialUiState
}

class HubsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val socialExtractor = SocialProfileExtractor()

    private val _gitHubState = MutableStateFlow<GitHubUiState>(GitHubUiState.Idle)
    val gitHubState: StateFlow<GitHubUiState> = _gitHubState.asStateFlow()

    private val _repoDetailState = MutableStateFlow<RepoDetailSheetState>(RepoDetailSheetState.Idle)
    val repoDetailState: StateFlow<RepoDetailSheetState> = _repoDetailState.asStateFlow()

    private val _socialState = MutableStateFlow<SocialUiState>(SocialUiState.Idle)
    val socialState: StateFlow<SocialUiState> = _socialState.asStateFlow()

    val rateLimitState: StateFlow<GitHubRateLimitStatus> = NetworkModule.gitHubRateLimit

    init {
        fetchGitHub("google")
    }

    fun fetchGitHub(rawInput: String, sort: String = "stars", languageFilter: String = "", page: Int = 1) {
        val query = rawInput.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            if (page == 1) _gitHubState.value = GitHubUiState.Loading
            try {
                val token = settingsRepo.settingsFlow.first().githubToken
                val auth = token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

                val cleaned = query
                    .removePrefix("https://github.com/")
                    .removePrefix("http://github.com/")
                    .removePrefix("github.com/")
                    .trim('/')

                val repos: List<GitHubRepo>
                var gists: List<GitHubGist> = emptyList()

                when {
                    cleaned.contains("/") && !cleaned.contains(" ") -> {
                        val parts = cleaned.split("/")
                        val repo = NetworkModule.gitHubApi.getRepo(parts[0], parts[1], auth)
                        repos = listOf(repo)
                    }
                    cleaned.contains(" ") || languageFilter.isNotBlank() -> {
                        val q = if (languageFilter.isNotBlank()) "$cleaned language:$languageFilter" else cleaned
                        repos = NetworkModule.gitHubApi.searchRepos(q, sort = sort, page = page, authHeader = auth).items
                    }
                    else -> {
                        val username = cleaned.removePrefix("@")
                        repos = runCatching {
                            NetworkModule.gitHubApi.getUserRepos(username, sort = "updated", page = page, authHeader = auth)
                        }.getOrElse {
                            NetworkModule.gitHubApi.searchRepos(cleaned, sort = sort, page = page, authHeader = auth).items
                        }
                        if (page == 1) {
                            gists = runCatching {
                                NetworkModule.gitHubApi.getUserGists(username, authHeader = auth)
                            }.getOrDefault(emptyList())
                        }
                    }
                }

                val existing = (_gitHubState.value as? GitHubUiState.Loaded)?.takeIf { page > 1 }
                val mergedRepos = if (existing != null) (existing.repos + repos).distinctBy { it.id } else repos
                _gitHubState.value = GitHubUiState.Loaded(
                    repos = mergedRepos,
                    gists = existing?.gists ?: gists,
                    page = page,
                    hasMore = repos.size >= 20,
                    currentQuery = query,
                )
            } catch (e: retrofit2.HttpException) {
                val msg = when (e.code()) {
                    403 -> "GitHub API rate limit exceeded. Add a Personal Access Token in Settings."
                    404 -> "Repository or user not found on GitHub."
                    else -> "GitHub API error (HTTP ${e.code()})"
                }
                _gitHubState.value = GitHubUiState.Error(msg)
            } catch (e: Exception) {
                _gitHubState.value = GitHubUiState.Error(e.localizedMessage ?: "Failed to load GitHub repositories.")
            }
        }
    }

    fun openRepoInspector(repo: GitHubRepo, path: String = "", branch: String? = null) {
        viewModelScope.launch {
            val prevLoaded = _repoDetailState.value as? RepoDetailSheetState.Loaded
            _repoDetailState.value = RepoDetailSheetState.Loading
            try {
                val token = settingsRepo.settingsFlow.first().githubToken
                val auth = token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
                val parts = repo.fullName.split("/")
                if (parts.size < 2) {
                    _repoDetailState.value = RepoDetailSheetState.Error("Invalid repository name")
                    return@launch
                }
                val owner = parts[0]
                val name = parts[1]
                val targetBranch = branch ?: prevLoaded?.takeIf { it.repo.id == repo.id }?.selectedBranch ?: repo.defaultBranch

                val branches = prevLoaded?.takeIf { it.repo.id == repo.id }?.branches
                    ?: runCatching { NetworkModule.gitHubApi.getRepoBranches(owner, name, authHeader = auth) }
                        .getOrDefault(listOf(GitHubBranch(repo.defaultBranch)))

                val releases = prevLoaded?.takeIf { it.repo.id == repo.id }?.releases
                    ?: runCatching { NetworkModule.gitHubApi.getRepoReleases(owner, name, authHeader = auth) }
                        .getOrDefault(emptyList())

                val tree = runCatching {
                    NetworkModule.gitHubApi.getRepoContents(owner, name, path, ref = targetBranch, authHeader = auth)
                }.getOrDefault(emptyList())

                val readme = runCatching {
                    val rm = NetworkModule.gitHubApi.getRepoReadme(owner, name, ref = targetBranch, authHeader = auth)
                    val decoded = Base64.decode(rm.content.replace("\n", ""), Base64.DEFAULT)
                    String(decoded, Charsets.UTF_8).take(10_000)
                }.getOrDefault("No README.md available.")

                _repoDetailState.value = RepoDetailSheetState.Loaded(
                    repo = repo,
                    branches = branches,
                    selectedBranch = targetBranch,
                    releases = releases,
                    currentPath = path,
                    treeNodes = tree.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() })),
                    readmeMarkdown = readme,
                )
            } catch (e: Exception) {
                _repoDetailState.value = RepoDetailSheetState.Error(e.localizedMessage ?: "Could not load repository details.")
            }
        }
    }

    fun dismissRepoSheet() {
        _repoDetailState.value = RepoDetailSheetState.Idle
    }

    fun fetchSocialMedia(
        platform: SocialPlatform,
        input: String,
        category: SocialMediaCategory,
    ) {
        viewModelScope.launch {
            _socialState.value = SocialUiState.Loading
            val res = socialExtractor.inspectInput(platform, input, category)
            res.onSuccess { items ->
                _socialState.value = SocialUiState.Loaded(items)
            }.onFailure { err ->
                _socialState.value = SocialUiState.Error(err.localizedMessage ?: "Failed to extract profile media.")
            }
        }
    }
}
