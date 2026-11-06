package com.zelsigns.app.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiConfig(val key: String, val baseUrl: String, val model: String)

data class RepoInfo(val owner: String, val name: String, val branch: String, val htmlUrl: String)

data class RunInfo(val id: Long, val status: String, val conclusion: String?, val htmlUrl: String)

class ApiException(message: String) : Exception(message)

private fun JsonObject.str(key: String): String? {
    val e = get(key)
    return if (e == null || e.isJsonNull) null else e.asString
}

object ApiClient {
    private const val API = "https://api.github.com"
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    val gson = Gson()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val BINARY_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "jar", "zip", "apk", "aar",
        "ttf", "otf", "woff", "woff2", "mp3", "mp4", "ogg", "wav", "pdf", "so", "dex", "bin", "keystore", "jks"
    )

    private suspend fun exec(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r -> r.code to (r.body?.string() ?: "") }
    }

    private fun short(s: String, n: Int = 300): String = if (s.length > n) s.take(n) + "…" else s

    private fun gh(pat: String, url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $pat")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun ghGet(pat: String, path: String): String {
        val (c, b) = exec(gh(pat, API + path).get().build())
        if (c !in 200..299) throw ApiException("GitHub $c: ${short(b)}")
        return b
    }

    private suspend fun ghSend(pat: String, method: String, path: String, json: Any): String {
        val body = gson.toJson(json).toRequestBody(JSON_MT)
        val (c, b) = exec(gh(pat, API + path).method(method, body).build())
        if (c !in 200..299) throw ApiException("GitHub $c: ${short(b)}")
        return b
    }

    // ───────────── Validation ─────────────

    /** GET /user — returns the login name. */
    suspend fun validateGithub(pat: String): String {
        if (pat.isBlank()) throw ApiException("GitHub token is empty")
        val b = ghGet(pat, "/user")
        return JsonParser.parseString(b).asJsonObject.str("login") ?: "unknown"
    }

    /** POST /chat/completions with a "ping" message. */
    suspend fun pingAi(cfg: AiConfig): String {
        if (cfg.key.isBlank()) throw ApiException("AI API key is empty")
        if (cfg.baseUrl.isBlank()) throw ApiException("AI base URL is empty")
        if (cfg.model.isBlank()) throw ApiException("AI model is empty")
        return chat(cfg, null, "ping", 8)
    }

    // ───────────── AI ─────────────

    suspend fun chat(cfg: AiConfig, system: String?, user: String, maxTokens: Int? = null): String {
        val msgs = mutableListOf<Map<String, String>>()
        if (system != null) msgs.add(mapOf("role" to "system", "content" to system))
        msgs.add(mapOf("role" to "user", "content" to user))
        val body = mutableMapOf<String, Any>(
            "model" to cfg.model,
            "messages" to msgs,
            "temperature" to 0.2
        )
        if (maxTokens != null) body["max_tokens"] = maxTokens
        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.key}")
            .post(gson.toJson(body).toRequestBody(JSON_MT))
            .build()
        val (c, b) = exec(req)
        if (c !in 200..299) throw ApiException("AI $c: ${short(b)}")
        return try {
            JsonParser.parseString(b).asJsonObject
                .getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        } catch (e: Exception) {
            throw ApiException("Unexpected AI response: ${short(b)}")
        }
    }

    // ───────────── Repo & commit ─────────────

    suspend fun createRepo(pat: String, name: String, private: Boolean): RepoInfo {
        val login = validateGithub(pat)
        val payload = mapOf(
            "name" to name,
            "private" to private,
            "auto_init" to true,
            "description" to "Built with Sign Zel"
        )
        val (c, b) = exec(
            gh(pat, "$API/user/repos").post(gson.toJson(payload).toRequestBody(JSON_MT)).build()
        )
        val json: String = when {
            c in 200..299 -> b
            c == 422 -> ghGet(pat, "/repos/$login/$name") // already exists → reuse
            else -> throw ApiException("GitHub $c: ${short(b)}")
        }
        val o = JsonParser.parseString(json).asJsonObject
        return RepoInfo(
            owner = o.getAsJsonObject("owner").str("login") ?: login,
            name = o.str("name") ?: name,
            branch = o.str("default_branch") ?: "main",
            htmlUrl = o.str("html_url") ?: "https://github.com/$login/$name"
        )
    }

    private fun isBinary(path: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in BINARY_EXT
    }

    /** Commits all files in a single commit through the Git Data API. Returns the commit SHA. */
    suspend fun commitFiles(pat: String, repo: RepoInfo, files: Map<String, ByteArray>, message: String): String {
        val base = "/repos/${repo.owner}/${repo.name}"

        var baseSha: String? = null
        for (i in 0 until 10) {
            val (c, b) = exec(gh(pat, "$API$base/git/ref/heads/${repo.branch}").get().build())
            if (c == 200) {
                baseSha = JsonParser.parseString(b).asJsonObject.getAsJsonObject("object").str("sha")
                break
            }
            delay(2000)
        }
        val parent: String = baseSha ?: throw ApiException("Could not read branch '${repo.branch}'")

        val commitJson = ghGet(pat, "$base/git/commits/$parent")
        val baseTree = JsonParser.parseString(commitJson).asJsonObject.getAsJsonObject("tree").str("sha")

        val entries = mutableListOf<Map<String, Any>>()
        for ((path, bytes) in files) {
            if (isBinary(path, bytes)) {
                val blob = ghSend(
                    pat, "POST", "$base/git/blobs",
                    mapOf("content" to Base64.encodeToString(bytes, Base64.NO_WRAP), "encoding" to "base64")
                )
                val sha = JsonParser.parseString(blob).asJsonObject.str("sha")
                    ?: throw ApiException("Blob upload failed for $path")
                entries.add(mapOf("path" to path, "mode" to "100644", "type" to "blob", "sha" to sha))
            } else {
                entries.add(
                    mapOf(
                        "path" to path, "mode" to "100644", "type" to "blob",
                        "content" to String(bytes, Charsets.UTF_8)
                    )
                )
            }
        }

        val treeBody = mutableMapOf<String, Any>("tree" to entries)
        if (baseTree != null) treeBody["base_tree"] = baseTree
        val treeJson = ghSend(pat, "POST", "$base/git/trees", treeBody)
        val treeSha = JsonParser.parseString(treeJson).asJsonObject.str("sha")
            ?: throw ApiException("Tree creation failed")

        val newCommit = ghSend(
            pat, "POST", "$base/git/commits",
            mapOf("message" to message, "tree" to treeSha, "parents" to listOf(parent))
        )
        val commitSha = JsonParser.parseString(newCommit).asJsonObject.str("sha")
            ?: throw ApiException("Commit creation failed")

        ghSend(pat, "PATCH", "$base/git/refs/heads/${repo.branch}", mapOf("sha" to commitSha, "force" to true))
        return commitSha
    }

    // ───────────── Actions ─────────────

    private fun toRun(o: JsonObject) = RunInfo(
        id = o.get("id").asLong,
        status = o.str("status") ?: "queued",
        conclusion = o.str("conclusion"),
        htmlUrl = o.str("html_url") ?: ""
    )

    suspend fun listRuns(pat: String, repo: RepoInfo, query: String): RunInfo? {
        val b = ghGet(pat, "/repos/${repo.owner}/${repo.name}/actions/runs?$query")
        val arr = JsonParser.parseString(b).asJsonObject.getAsJsonArray("workflow_runs")
        return if (arr == null || arr.size() == 0) null else toRun(arr[0].asJsonObject)
    }

    suspend fun findRunForSha(pat: String, repo: RepoInfo, sha: String): RunInfo? =
        listRuns(pat, repo, "head_sha=$sha&per_page=5")

    suspend fun latestRun(pat: String, repo: RepoInfo): RunInfo? =
        listRuns(pat, repo, "branch=${repo.branch}&per_page=1")

    suspend fun getRun(pat: String, repo: RepoInfo, id: Long): RunInfo {
        val b = ghGet(pat, "/repos/${repo.owner}/${repo.name}/actions/runs/$id")
        return toRun(JsonParser.parseString(b).asJsonObject)
    }

    suspend fun dispatchWorkflow(pat: String, repo: RepoInfo) {
        ghSend(
            pat, "POST",
            "/repos/${repo.owner}/${repo.name}/actions/workflows/build-apk.yml/dispatches",
            mapOf("ref" to repo.branch)
        )
    }

    /** Downloads logs of failed jobs and condenses them to the useful error lines. */
    suspend fun failureLogs(pat: String, repo: RepoInfo, runId: Long): String {
        val jobsJson = ghGet(pat, "/repos/${repo.owner}/${repo.name}/actions/runs/$runId/jobs")
        val jobs = JsonParser.parseString(jobsJson).asJsonObject.getAsJsonArray("jobs")
        val sb = StringBuilder()
        if (jobs != null) {
            for (j in jobs) {
                val o = j.asJsonObject
                if (o.str("conclusion") == "failure") {
                    val id = o.get("id").asLong
                    val (c, b) = exec(
                        gh(pat, "$API/repos/${repo.owner}/${repo.name}/actions/jobs/$id/logs").get().build()
                    )
                    if (c in 200..299) sb.append(b).append('\n')
                }
            }
        }
        return condense(sb.toString())
    }

    private val ts = Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
    private val interesting = Regex(
        "error|\\be: |FAILED|went wrong|caused by|could not|unresolved|exception|not found|cannot|invalid",
        RegexOption.IGNORE_CASE
    )

    private fun condense(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.lines().map { it.replace(ts, "") }
        val key = lines.filter { interesting.containsMatchIn(it) }.takeLast(70)
        val tail = lines.takeLast(40)
        val text = "=== KEY ERROR LINES ===\n" + key.joinToString("\n") +
            "\n=== LOG TAIL ===\n" + tail.joinToString("\n")
        return if (text.length > 9000) text.takeLast(9000) else text
    }
}
