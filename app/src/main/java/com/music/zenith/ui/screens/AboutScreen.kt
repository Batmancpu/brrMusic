package com.music.zenith.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.zenith.BuildConfig
import com.music.zenith.R
import com.music.zenith.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.time.Instant

private const val FALLBACK_REPO = "rezapranata089-bit/Zenith"
private const val MAX_COMMITS = 30

private data class ChangeCommit(
    val sha: String,
    val subject: String,
    val author: String,
    val whenMillis: Long,
    val url: String,
)

private sealed interface ChangesState {
    data object Loading : ChangesState
    data class Loaded(val commits: List<ChangeCommit>) : ChangesState
    data object Failed : ChangesState
}

private val changesCache = HashMap<String, List<ChangeCommit>>()
private val aboutJson = Json { ignoreUnknownKeys = true }
private val conventionalSubject = Regex("^(\\w+)(?:\\([^)]*\\))?!?:\\s*(.+)$")
private val conventionalTypes = setOf(
    "feat", "fix", "perf", "refactor", "docs", "style", "test", "build", "ci", "chore", "revert",
)

/**
 * The About page: app icon, name and version on top, then project links,
 * the commits that make up this version (from the GitHub repo) and credits.
 */
@Composable
internal fun AboutContent(version: String) {
    val context = LocalContext.current
    val repo = remember { BuildConfig.UPDATE_REPO.ifBlank { FALLBACK_REPO } }
    val tag = remember(version) { "v" + version.substringBefore('-') }
    val repoUrl = "https://github.com/$repo"

    AboutHero(version = version)

    SettingsGroup(header = "Project") {
        SettingsRow(
            icon = Icons.Rounded.Code,
            title = "Source code",
            subtitle = repo,
            onClick = { context.openAboutLink(repoUrl) },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.NewReleases,
            title = "Releases",
            onClick = { context.openAboutLink("$repoUrl/releases") },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.BugReport,
            title = "Report an issue",
            onClick = { context.openAboutLink("$repoUrl/issues") },
        )
    }

    ChangesGroup(repo = repo, tag = tag, repoUrl = repoUrl)

    SettingsGroup(header = "Credits") {
        SettingsRow(
            icon = Icons.Rounded.Favorite,
            title = "BitChord",
            subtitle = "The open-source app Zenith is forked from",
            onClick = { context.openAboutLink("https://github.com/kushagrasinghx/BitChord") },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.AutoAwesome,
            title = "Backdrop by Kyant0",
            subtitle = "Liquid glass engine \u00b7 Apache-2.0",
            onClick = { context.openAboutLink("https://github.com/Kyant0/backdrop") },
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AboutHero(version: String) {
    val context = LocalContext.current
    val icon = remember(context) { context.appIconBitmap(432) }
    val accent = MaterialTheme.colorScheme.primary
    val iconShape = RoundedCornerShape(21.dp)
    val buildType = BuildConfig.BUILD_TYPE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, 76.dp.toPx()),
                        radius = 200.dp.toPx(),
                    ),
                )
            }
            .padding(top = 24.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
                    .shadow(elevation = 14.dp, shape = iconShape, clip = true)
                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), iconShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, iconShape),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = context.getString(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A YouTube Music player with an Apple Music\u2013style interface",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(percent = 50),
                )
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (buildType == "release") "Version $version" else "Version $version \u00b7 $buildType",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangesGroup(repo: String, tag: String, repoUrl: String) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ChangesState>(ChangesState.Loading, repo, tag, attempt) {
        value = ChangesState.Loading
        value = loadChanges(repo, tag)?.let { ChangesState.Loaded(it) } ?: ChangesState.Failed
    }

    SettingsGroup(header = "What's in $tag") {
        when (val s = state) {
            is ChangesState.Loading -> ChangesLoadingRow()
            is ChangesState.Failed -> SettingsRow(
                icon = Icons.Rounded.Refresh,
                title = "Couldn't load changes",
                subtitle = "Check your connection, then tap to retry.",
                onClick = { attempt++ },
            )
            is ChangesState.Loaded -> {
                if (s.commits.isEmpty()) {
                    SettingsRow(title = "No commits found for this version")
                } else {
                    s.commits.forEachIndexed { index, commit ->
                        if (index > 0) RowDivider()
                        CommitRow(commit) { context.openAboutLink(commit.url) }
                    }
                }
            }
        }
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.Description,
            title = "Full release notes",
            onClick = { context.openAboutLink("$repoUrl/releases/tag/$tag") },
        )
    }
}

@Composable
private fun ChangesLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(ICON_SIZE - 4.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(ICON_GAP + 2.dp))
        Text(
            text = "Loading changes\u2026",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommitRow(commit: ChangeCommit, onClick: () -> Unit) {
    val (type, title) = splitSubject(commit.subject)
    val meta = listOfNotNull(
        commit.author.ifBlank { null },
        if (commit.whenMillis > 0L) {
            DateUtils.getRelativeTimeSpanString(
                commit.whenMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        } else {
            null
        },
    ).joinToString(" \u00b7 ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type != null) Badge(type)
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = commit.sha.take(7),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun splitSubject(subject: String): Pair<String?, String> {
    val match = conventionalSubject.find(subject) ?: return null to subject
    val type = match.groupValues[1].lowercase()
    return if (type in conventionalTypes) type to match.groupValues[2] else null to subject
}

private suspend fun loadChanges(repo: String, tag: String): List<ChangeCommit>? {
    val key = "$repo@$tag"
    changesCache[key]?.let { return it }
    val result = withContext(Dispatchers.IO) { runCatching { fetchChanges(repo, tag) }.getOrNull() }
    if (result != null) changesCache[key] = result
    return result
}

private fun getJson(url: String): JsonElement? {
    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .build()
    val body = Http.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) null else response.body?.string()
    } ?: return null
    return aboutJson.parseToJsonElement(body)
}

/**
 * Commits between the previous release and this one. A first release lists
 * its own history; a build whose tag is not published yet (a beta) lists the
 * latest commits instead.
 */
private fun fetchChanges(repo: String, tag: String): List<ChangeCommit>? {
    val api = "https://api.github.com/repos/$repo"
    val tags = (getJson("$api/releases?per_page=30") as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.get("tag_name")?.jsonPrimitive?.contentOrNull }
        .orEmpty()
    val at = tags.indexOf(tag)
    val previous = if (at >= 0) tags.getOrNull(at + 1) else null
    val element = when {
        previous != null -> getJson("$api/compare/$previous...$tag")
        at >= 0 -> getJson("$api/commits?sha=$tag&per_page=$MAX_COMMITS")
        else -> getJson("$api/commits?per_page=$MAX_COMMITS")
    } ?: return null

    // compare lists oldest first, commits newest first: show newest first.
    val items: List<JsonElement> = when (element) {
        is JsonArray -> element
        is JsonObject -> (element["commits"] as? JsonArray).orEmpty().reversed()
        else -> return null
    }
    return items.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val commit = obj["commit"] as? JsonObject ?: return@mapNotNull null
        val subject = commit["message"]?.jsonPrimitive?.contentOrNull
            .orEmpty().lineSequence().firstOrNull().orEmpty().trim()
        if (subject.isEmpty() || subject.startsWith("Merge ")) return@mapNotNull null
        val authorObj = commit["author"] as? JsonObject
        val author = authorObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val millis = authorObj?.get("date")?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L
        val url = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$repo/commit/$sha"
        ChangeCommit(sha, subject, author, millis, url)
    }.take(MAX_COMMITS)
}

private fun Context.appIconBitmap(sizePx: Int): ImageBitmap? = runCatching {
    val drawable = packageManager.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
}.getOrNull()

private fun Context.openAboutLink(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
    }
}