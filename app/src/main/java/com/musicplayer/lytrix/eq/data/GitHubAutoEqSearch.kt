package com.musicplayer.lytrix.eq.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * GitHub-backed search interface for the AutoEq measurement database (ndellagrotte/AutoEq).
 * Uses the GitHub Trees API to index models and fetches individual ParametricEQ.txt profiles on demand.
 */
class GitHubAutoEqSearch(context: Context) {

    private val entries = mutableListOf<AutoEqEntry>()
    private var isIndexed = false

    private val rigLookupCache = mutableMapOf<String, Map<String, String>>()
    private val cacheDir = File(context.applicationContext.filesDir, "autoeq_cache")
    private val treeFile = File(cacheDir, "tree.json")
    private val treeTimestampFile = File(cacheDir, "tree_timestamp")

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GitHubAutoEqSearch"
        private const val REPO_OWNER = "ndellagrotte"
        private const val REPO_NAME = "AutoEq"
        private const val BRANCH = "master"
        private const val TREE_API_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/git/trees/$BRANCH?recursive=1"
        private const val RAW_BASE_URL =
            "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$BRANCH"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    @Serializable
    private data class GitHubTreeResponse(
        val sha: String = "",
        val tree: List<GitHubTreeNode> = emptyList(),
        val truncated: Boolean = false
    )

    @Serializable
    private data class GitHubTreeNode(
        val path: String,
        val mode: String? = null,
        val type: String,
        val sha: String? = null,
        val size: Long? = null
    )

    fun isDatabaseCached(): Boolean = treeFile.exists()

    suspend fun buildIndex(forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (isIndexed && entries.isNotEmpty() && !forceRefresh) {
            return@withContext true
        }
        try {
            cacheDir.mkdirs()

            val treeJson = getCachedOrFetchTree(forceRefresh) ?: return@withContext false
            val treeResponse = json.decodeFromString<GitHubTreeResponse>(treeJson)

            if (treeResponse.truncated) {
                Timber.tag(TAG).w("GitHub tree response truncated; partial index loaded")
            }

            val eqNodes = treeResponse.tree.filter { node ->
                node.type == "blob" &&
                    node.path.startsWith("results/") &&
                    node.path.endsWith(" ParametricEQ.txt")
            }

            val parsed = ArrayList<AutoEqEntry>(eqNodes.size)
            for (node in eqNodes) {
                parseEntryFromPath(node.path)?.let { parsed.add(it) }
            }

            entries.clear()
            entries.addAll(parsed)
            isIndexed = true
            Timber.tag(TAG).d("Indexed ${entries.size} AutoEq entries")
            entries.isNotEmpty()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to build AutoEq index")
            false
        }
    }

    private fun parseEntryFromPath(path: String): AutoEqEntry? {
        val parts = path.split("/")
        if (parts.size < 5) return null

        val source = parts[1]
        val formRigDir = parts[2]
        val headphoneName = parts[3]
        val (rigFromDir, parsedForm) = parseFormAndRig(formRigDir)

        val finalRig = when {
            rigFromDir != "unknown" && rigFromDir.isNotBlank() -> rigFromDir
            source == "Headphone.com Legacy" || source == "Innerfidelity" -> "HMS II.3"
            else -> lookupRigInNameIndex(source, headphoneName) ?: "unknown"
        }

        return AutoEqEntry(
            label = headphoneName,
            form = parsedForm,
            rig = finalRig,
            source = source,
            formDirectory = formRigDir
        )
    }

    private fun getCachedOrFetchTree(forceRefresh: Boolean): String? {
        if (!forceRefresh && treeFile.exists() && treeTimestampFile.exists()) {
            val timestamp = treeTimestampFile.readText().trim().toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - timestamp < CACHE_TTL_MS) {
                return treeFile.readText()
            }
        }

        return try {
            val request = Request.Builder()
                .url(TREE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("GitHub API ${response.code}: ${response.message}")
                    return if (treeFile.exists()) treeFile.readText() else null
                }
                val body = response.body?.string() ?: return null
                treeFile.writeText(body)
                treeTimestampFile.writeText(System.currentTimeMillis().toString())
                body
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch AutoEq tree")
            if (treeFile.exists()) treeFile.readText() else null
        }
    }

    private fun fetchRawFile(repoPath: String): String? {
        val cacheFile = File(cacheDir, repoPath.replace("/", File.separator))
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL_MS) {
            return cacheFile.readText()
        }

        return try {
            val url = "$RAW_BASE_URL/${repoPath.encodePathSegments()}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return if (cacheFile.exists()) cacheFile.readText() else null
                }
                val body = response.body?.string() ?: return null
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(body)
                body
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch raw file: $repoPath")
            if (cacheFile.exists()) cacheFile.readText() else null
        }
    }

    private fun String.encodePathSegments(): String {
        return split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    fun searchModels(query: String, maxResults: Int = 100): Map<String, List<AutoEqEntry>> {
        if (!isIndexed) return emptyMap()
        val lowerQuery = query.lowercase().trim()

        val filtered = if (lowerQuery.isBlank()) {
            entries
        } else {
            entries.filter { normalizeModelName(it.label).lowercase().contains(lowerQuery) }
        }

        val grouped = filtered.groupBy { normalizeModelName(it.label) }
        val sortedNames = grouped.keys.sortedWith(
            compareByDescending<String> { name ->
                val lower = name.lowercase()
                when {
                    lowerQuery.isEmpty() -> 0
                    lower == lowerQuery -> 2000
                    lower.startsWith(lowerQuery) -> 1000
                    else -> 100
                }
            }.thenBy { it }
        ).take(maxResults)

        return sortedNames.associateWith { grouped.getValue(it) }
    }

    fun getVariantsForModel(modelName: String): List<AutoEqEntry> {
        val normalized = normalizeModelName(modelName)
        return entries.filter {
            normalizeModelName(it.label).equals(normalized, ignoreCase = true)
        }
    }

    suspend fun loadEQ(entry: AutoEqEntry): ParametricEQ? = withContext(Dispatchers.IO) {
        try {
            val eqPath = "results/${entry.source}/${entry.formDirectory}/${entry.label}/${entry.label} ParametricEQ.txt"
            val content = fetchRawFile(eqPath) ?: return@withContext null
            ParametricEQParser.parseText(content)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load EQ for ${entry.label}")
            null
        }
    }

    private fun normalizeModelName(modelName: String): String {
        return modelName.replace(Regex("""\s*\([^)]*\)\s*"""), "").trim()
    }

    private fun lookupRigInNameIndex(source: String, headphoneName: String): String? {
        return try {
            if (!rigLookupCache.containsKey(source)) {
                loadNameIndexForSource(source)
            }
            rigLookupCache[source]?.get(headphoneName)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadNameIndexForSource(source: String) {
        val content = fetchRawFile("measurements/$source/name_index.tsv")
        if (content == null) {
            rigLookupCache[source] = emptyMap()
            return
        }
        val rigMap = mutableMapOf<String, String>()
        content.lines().drop(1).forEach { line ->
            val cols = line.split("\t")
            if (cols.size >= 5) {
                val name = cols[2].trim()
                val rig = cols[4].trim()
                if (name.isNotBlank() && rig.isNotBlank() && rig != "ignore") {
                    rigMap[name] = rig
                }
            }
        }
        rigLookupCache[source] = rigMap
    }

    private fun parseFormAndRig(formRig: String): Pair<String, String> {
        val formKeywords = listOf("in-ear", "over-ear", "earbud")
        val foundForm = formKeywords.firstOrNull { formRig.contains(it, ignoreCase = true) } ?: "unknown"
        val rig = formRig.replace(foundForm, "", ignoreCase = true).trim()
        return Pair(rig.ifEmpty { "unknown" }, foundForm)
    }
}
