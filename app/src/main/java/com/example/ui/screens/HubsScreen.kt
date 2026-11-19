package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadSource
import com.example.data.network.GitHubRateLimitStatus
import com.example.data.network.GitHubRepo
import com.example.data.social.ExtractedSocialMedia
import com.example.data.social.SocialMediaCategory
import com.example.data.social.SocialPlatform
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.PrismGradientButton
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.GlassBase
import com.example.ui.theme.GlassEdgeLo
import com.example.ui.theme.GlassHi
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.OmniInsetBg
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.GitHubUiState
import com.example.viewmodel.RepoDetailSheetState
import com.example.viewmodel.SocialUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubsScreen(
    initialTab: Int,
    gitHubState: GitHubUiState,
    rateLimitState: GitHubRateLimitStatus,
    repoDetailState: RepoDetailSheetState,
    socialState: SocialUiState,
    onSearchGitHub: (query: String, sort: String, languageFilter: String, page: Int) -> Unit,
    onOpenRepoDetail: (GitHubRepo, path: String, branch: String?) -> Unit,
    onDismissRepoDetail: () -> Unit,
    onFetchSocial: (SocialPlatform, String, SocialMediaCategory) -> Unit,
    onDownloadUrl: (url: String, title: String, source: DownloadSource) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onAnalyzeRepoWithAi: (GitHubRepo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current
    var selectedHubTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 1000.dp).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                Row(modifier = Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GitHub Hub", "Social (@handle)").forEachIndexed { idx, title ->
                        val active = selectedHubTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) Brush.linearGradient(listOf(accent.primary, accent.secondary))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    CircleShape,
                                )
                                .clickable { selectedHubTab = idx }
                                .testTag("hub_tab_$idx"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(title, color = if (active) Color.White else TextMuted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (selectedHubTab == 0) {
                GitHubHubContent(
                    gitHubState = gitHubState,
                    rateLimitState = rateLimitState,
                    onSearch = onSearchGitHub,
                    onOpenInspector = { repo -> onOpenRepoDetail(repo, "", null) },
                    onDownloadZip = { repo ->
                        onDownloadUrl(repo.archiveZipUrl(repo.defaultBranch), "${repo.name}-${repo.defaultBranch}.zip", DownloadSource.GITHUB)
                    },
                    onDownloadGist = { url, name -> onDownloadUrl(url, name, DownloadSource.GITHUB) },
                    onAnalyzeRepo = onAnalyzeRepoWithAi,
                )
            } else {
                SocialHubContent(
                    socialState = socialState,
                    onFetch = onFetchSocial,
                    onDownloadBatch = { items ->
                        items.filter { it.isDirectlyDownloadable }.forEach { item ->
                            onDownloadUrl(item.downloadUrl, item.title, DownloadSource.SOCIAL)
                        }
                    },
                    onOpenInBrowser = onOpenInBrowser,
                )
            }
        }

        if (repoDetailState !is RepoDetailSheetState.Idle) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = onDismissRepoDetail, sheetState = sheetState, containerColor = SurfaceElevated, contentColor = TextPrimary) {
                when (repoDetailState) {
                    is RepoDetailSheetState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accent.primary)
                        }
                    }
                    is RepoDetailSheetState.Error -> {
                        Text(repoDetailState.message, color = StatusError, modifier = Modifier.padding(24.dp))
                    }
                    is RepoDetailSheetState.Loaded -> {
                        var sheetTab by remember { mutableIntStateOf(0) }
                        var assetTypeFilter by remember { mutableStateOf("All") }

                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(repoDetailState.repo.fullName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)

                            // Branch selector
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(repoDetailState.branches, key = { it.name }) { br ->
                                    val chosen = br.name == repoDetailState.selectedBranch
                                    Box(
                                        modifier = Modifier.clip(CircleShape).background(if (chosen) accent.primary.copy(alpha = 0.25f) else GlassBase).border(1.dp, if (chosen) accent.primary else GlassEdgeLo, CircleShape).clickable {
                                            onOpenRepoDetail(repoDetailState.repo, "", br.name)
                                        }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text("branch: ${br.name}", color = if (chosen) TextPrimary else TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMonoFamily)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth().clip(CircleShape).background(OmniInsetBg).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Releases", "Files Tree", "README").forEachIndexed { idx, label ->
                                    val active = sheetTab == idx
                                    Box(
                                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (active) accent.primary.copy(alpha = 0.30f) else Color.Transparent).clickable { sheetTab = idx }.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, color = if (active) TextPrimary else TextMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            when (sheetTab) {
                                0 -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("All", "APK", "ZIP").forEach { ft ->
                                            val chosen = assetTypeFilter == ft
                                            Box(
                                                modifier = Modifier.clip(CircleShape).background(if (chosen) GlassHi else GlassBase).border(1.dp, if (chosen) accent.orb3 else GlassEdgeLo, CircleShape).clickable { assetTypeFilter = ft }.padding(horizontal = 10.dp, vertical = 5.dp),
                                            ) {
                                                Text(ft, color = if (chosen) TextPrimary else TextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        item {
                                            PrismGradientButton(
                                                text = "Download Branch (${repoDetailState.selectedBranch}.zip)",
                                                icon = Icons.Outlined.FolderZip,
                                                onClick = {
                                                    onDownloadUrl(
                                                        repoDetailState.repo.archiveZipUrl(repoDetailState.selectedBranch),
                                                        "${repoDetailState.repo.name}-${repoDetailState.selectedBranch}.zip",
                                                        DownloadSource.GITHUB,
                                                    )
                                                    onDismissRepoDetail()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        if (repoDetailState.releases.isEmpty()) {
                                            item { Text("No tagged releases found. Use Branch ZIP or Files Tree.", color = TextMuted, fontSize = 13.sp) }
                                        } else {
                                            items(repoDetailState.releases, key = { it.id }) { release ->
                                                val matchingAssets = release.assets.filter { a ->
                                                    when (assetTypeFilter) {
                                                        "APK" -> a.name.endsWith(".apk", true)
                                                        "ZIP" -> a.name.endsWith(".zip", true) || a.name.endsWith(".tar.gz", true)
                                                        else -> true
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassBase).border(1.dp, GlassEdgeLo, RoundedCornerShape(18.dp)).padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(release.name?.takeIf { it.isNotBlank() } ?: release.tagName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                    matchingAssets.forEach { asset ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OmniInsetBg).clickable {
                                                                onDownloadUrl(asset.downloadUrl, asset.name, DownloadSource.GITHUB)
                                                                onDismissRepoDetail()
                                                            }.padding(horizontal = 12.dp, vertical = 9.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("${asset.name} (${asset.size / 1024} KB)", color = TextPrimary, fontSize = 12.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Icon(Icons.Rounded.Download, contentDescription = null, tint = accent.orb3, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (repoDetailState.currentPath.isNotBlank()) {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassBase).clickable {
                                                        val parent = repoDetailState.currentPath.substringBeforeLast('/', "")
                                                        onOpenRepoDetail(repoDetailState.repo, parent, repoDetailState.selectedBranch)
                                                    }.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up", tint = accent.orb3, modifier = Modifier.size(16.dp))
                                                    Text(".. /${repoDetailState.currentPath}", color = accent.orb3, fontSize = 12.5.sp, fontFamily = JetBrainsMonoFamily)
                                                }
                                            }
                                        }
                                        items(repoDetailState.treeNodes, key = { it.path }) { node ->
                                            val isDir = node.type == "dir"
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OmniInsetBg).clickable {
                                                    if (isDir) onOpenRepoDetail(repoDetailState.repo, node.path, repoDetailState.selectedBranch)
                                                    else if (!node.downloadUrl.isNullOrBlank()) {
                                                        onDownloadUrl(node.downloadUrl, node.name, DownloadSource.GITHUB)
                                                        onDismissRepoDetail()
                                                    }
                                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                                    Icon(if (isDir) Icons.Outlined.Folder else Icons.Outlined.Description, contentDescription = null, tint = if (isDir) accent.orb4 else TextMuted, modifier = Modifier.size(18.dp))
                                                    Text(node.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                if (!isDir) Icon(Icons.Rounded.Download, contentDescription = "Download file", tint = accent.orb3, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Column(modifier = Modifier.fillMaxWidth().height(340.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                                        Text(repoDetailState.readmeMarkdown, color = TextMuted, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily, lineHeight = 19.sp)
                                    }
                                }
                            }
                        }
                    }
                    RepoDetailSheetState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun GitHubHubContent(
    gitHubState: GitHubUiState,
    rateLimitState: GitHubRateLimitStatus,
    onSearch: (query: String, sort: String, languageFilter: String, page: Int) -> Unit,
    onOpenInspector: (GitHubRepo) -> Unit,
    onDownloadZip: (GitHubRepo) -> Unit,
    onDownloadGist: (String, String) -> Unit,
    onAnalyzeRepo: (GitHubRepo) -> Unit,
) {
    val accent = LocalPrismColors.current
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("google") }
    var selectedLang by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf("stars") }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "gh_search") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("GitHub Hub", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        if (rateLimitState.limit > 0) {
                            Box(
                                modifier = Modifier.clip(CircleShape).background(if (rateLimitState.isExhausted) StatusError.copy(alpha = 0.2f) else GlassBase).padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "API ${rateLimitState.remaining}/${rateLimitState.limit}",
                                    color = if (rateLimitState.isExhausted) StatusError else accent.orb3,
                                    fontSize = 10.5.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Username, owner/repo, or keyword...", color = TextMuted, fontSize = 14.sp)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily),
                                cursorBrush = SolidColor(accent.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); onSearch(query, selectedSort, selectedLang, 1) }),
                                modifier = Modifier.fillMaxWidth().testTag("github_search_input"),
                            )
                        }
                        PrismGradientButton(
                            text = "Fetch",
                            height = 36.dp,
                            onClick = { focusManager.clearFocus(); onSearch(query, selectedSort, selectedLang, 1) },
                            modifier = Modifier.testTag("github_fetch_button"),
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("" to "All Lang", "Kotlin" to "Kotlin", "Python" to "Python", "TypeScript" to "TS", "Rust" to "Rust", "Go" to "Go")) { (langVal, label) ->
                            val active = selectedLang == langVal
                            Box(
                                modifier = Modifier.clip(CircleShape).background(if (active) GlassHi else GlassBase).border(1.dp, if (active) accent.primary else GlassEdgeLo, CircleShape).clickable {
                                    selectedLang = langVal
                                    onSearch(query, selectedSort, selectedLang, 1)
                                }.padding(horizontal = 11.dp, vertical = 5.dp),
                            ) {
                                Text(label, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                            }
                        }
                        items(listOf("stars" to "Sort: Stars", "updated" to "Sort: Recent")) { (sVal, sLbl) ->
                            val active = selectedSort == sVal
                            Box(
                                modifier = Modifier.clip(CircleShape).background(if (active) accent.orb3.copy(alpha = 0.2f) else GlassBase).border(1.dp, if (active) accent.orb3 else GlassEdgeLo, CircleShape).clickable {
                                    selectedSort = sVal
                                    onSearch(query, selectedSort, selectedLang, 1)
                                }.padding(horizontal = 11.dp, vertical = 5.dp),
                            ) {
                                Text(sLbl, color = if (active) TextPrimary else TextMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        when (gitHubState) {
            GitHubUiState.Idle -> Unit
            GitHubUiState.Loading -> {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent.primary) } }
            }
            is GitHubUiState.Error -> {
                item { LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text(gitHubState.message, color = StatusError, modifier = Modifier.padding(18.dp)) } }
            }
            is GitHubUiState.Loaded -> {
                if (gitHubState.gists.isNotEmpty()) {
                    item(key = "gists_strip") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(gitHubState.gists.take(8), key = { it.id }) { gist ->
                                val firstFile = gist.files.values.firstOrNull()
                                if (firstFile != null) {
                                    Row(
                                        modifier = Modifier.clip(CircleShape).background(GlassBase).border(1.dp, GlassEdgeLo, CircleShape).clickable { onDownloadGist(firstFile.rawUrl, firstFile.filename) }.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, tint = accent.orb4, modifier = Modifier.size(14.dp))
                                        Text("Gist: ${firstFile.filename}", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                items(gitHubState.repos, key = { it.id }) { repo ->
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AsyncImage(model = repo.owner.avatarUrl, contentDescription = repo.owner.login, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(CircleShape).background(GlassHi))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.fullName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Star, contentDescription = null, tint = accent.orb4, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("${repo.stars}", color = TextMuted, fontSize = 11.5.sp, fontFamily = JetBrainsMonoFamily)
                                        }
                                        if (!repo.language.isNullOrBlank()) Text(repo.language, color = accent.orb3, fontSize = 11.5.sp)
                                        repo.license?.spdxId?.takeIf { it.isNotBlank() && it != "NOASSERTION" }?.let { lic ->
                                            Text(lic, color = TextMuted, fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily)
                                        }
                                    }
                                }
                                IconButton(onClick = { onAnalyzeRepo(repo) }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = "Analyze repo with AI", tint = accent.secondary, modifier = Modifier.size(18.dp))
                                }
                            }

                            if (!repo.description.isNullOrBlank()) {
                                Text(repo.description, color = TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).clip(CircleShape).background(GlassBase).border(1.dp, GlassEdgeLo, CircleShape).clickable { onOpenInspector(repo) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = accent.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Releases & Tree", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).clip(CircleShape).background(accent.primary.copy(alpha = 0.22f)).border(1.dp, accent.primary.copy(alpha = 0.55f), CircleShape).clickable { onDownloadZip(repo) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Source .zip", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                if (gitHubState.hasMore) {
                    item(key = "gh_load_more") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            PrismGradientButton(
                                text = "Load More Repositories",
                                height = 38.dp,
                                onClick = { onSearch(gitHubState.currentQuery, selectedSort, selectedLang, gitHubState.page + 1) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialHubContent(
    socialState: SocialUiState,
    onFetch: (SocialPlatform, String, SocialMediaCategory) -> Unit,
    onDownloadBatch: (List<ExtractedSocialMedia>) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val accent = LocalPrismColors.current
    val focusManager = LocalFocusManager.current
    var selectedPlatform by remember { mutableStateOf(SocialPlatform.YOUTUBE) }
    var selectedCategory by remember { mutableStateOf(SocialMediaCategory.ALL) }
    var handleInput by remember { mutableStateOf("@GoogleDevelopers") }
    val checkedIds = remember { mutableStateListOf<String>() }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "social_controls") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Social Media Hub (8 Platforms)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SocialPlatform.entries) { p ->
                            val chosen = p == selectedPlatform
                            Row(
                                modifier = Modifier.defaultMinSize(minHeight = 38.dp).clip(CircleShape).background(if (chosen) Color(p.badgeColorHex).copy(alpha = 0.28f) else GlassBase).border(1.dp, if (chosen) Color(p.badgeColorHex) else GlassEdgeLo, CircleShape).clickable { selectedPlatform = p }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.label, color = if (chosen) TextPrimary else TextMuted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(OmniInsetBg, CircleShape).border(1.dp, GlassEdgeLo, CircleShape).padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (handleInput.isEmpty()) Text("Enter @username or post/video URL...", color = TextMuted, fontSize = 14.sp)
                            BasicTextField(
                                value = handleInput,
                                onValueChange = { handleInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = FigtreeFontFamily),
                                cursorBrush = SolidColor(accent.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); checkedIds.clear(); onFetch(selectedPlatform, handleInput, selectedCategory) }),
                                modifier = Modifier.fillMaxWidth().testTag("social_handle_input"),
                            )
                        }
                        PrismGradientButton(
                            text = "Extract",
                            height = 36.dp,
                            onClick = { focusManager.clearFocus(); checkedIds.clear(); onFetch(selectedPlatform, handleInput, selectedCategory) },
                            modifier = Modifier.testTag("social_extract_button"),
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SocialMediaCategory.entries) { cat ->
                            val chosen = cat == selectedCategory
                            Box(
                                modifier = Modifier.clip(CircleShape).background(if (chosen) GlassHi else GlassBase).border(1.dp, if (chosen) accent.primary else GlassEdgeLo, CircleShape).clickable { selectedCategory = cat }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(cat.label, color = if (chosen) TextPrimary else TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        when (socialState) {
            SocialUiState.Idle -> {
                item {
                    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Text(
                            text = "Select any platform above, enter a @handle or post URL, and tap Extract to discover publicly accessible media assets.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
            SocialUiState.Loading -> {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent.secondary) } }
            }
            is SocialUiState.Error -> {
                item { LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text(socialState.message, color = StatusError, modifier = Modifier.padding(18.dp)) } }
            }
            is SocialUiState.Loaded -> {
                val downloadable = socialState.items.filter { it.isDirectlyDownloadable }
                val selectedItems = downloadable.filter { it.id in checkedIds }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${socialState.items.size} found (${downloadable.size} downloadable)", color = TextMuted, fontSize = 12.5.sp)
                        if (downloadable.isNotEmpty()) {
                            PrismGradientButton(
                                text = if (selectedItems.isEmpty()) "Download Eligible (${downloadable.size})" else "Download (${selectedItems.size})",
                                height = 38.dp,
                                icon = Icons.Rounded.Download,
                                onClick = { onDownloadBatch(selectedItems.ifEmpty { downloadable }) },
                            )
                        }
                    }
                }

                items(socialState.items, key = { it.id }) { item ->
                    val checked = item.id in checkedIds
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth().then(
                            if (item.isDirectlyDownloadable) Modifier.clickable { if (checked) checkedIds.remove(item.id) else checkedIds.add(item.id) }
                            else Modifier
                        ),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (item.isDirectlyDownloadable) {
                                Icon(
                                    imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    contentDescription = "Select item",
                                    tint = if (checked) accent.orb3 else TextMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.clip(CircleShape).background(Color(item.platform.badgeColorHex).copy(alpha = 0.25f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(item.formatBadge, color = Color(item.platform.badgeColorHex), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = if (item.isDirectlyDownloadable) "DOWNLOADABLE" else "BROWSER PREVIEW",
                                        color = if (item.isDirectlyDownloadable) StatusSuccess else StatusWarning,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.previewInfo, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onOpenInBrowser(item.downloadUrl) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.Language, contentDescription = "Open in Prism Browser", tint = accent.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
