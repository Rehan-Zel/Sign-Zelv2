package com.zelsigns.app.data

data class AiFile(val path: String?, val content: String?)

data class AiResult(val summary: String?, val files: List<AiFile>?)

object AiAssistant {

    private const val GEN_SYSTEM = """You are a senior Android build engineer. You make arbitrary projects compile into a debug APK using GitHub Actions.
Return ONLY one JSON object, with no markdown fences and no prose, in exactly this shape:
{"summary":"short description of what you generated","files":[{"path":"relative/path","content":"full file text"}]}
Hard rules:
- Text files only. Never re-create files the user already has.
- Toolchain: JDK 17, Gradle 8.7, AGP 8.4.0, Kotlin 1.9.24, compileSdk 34, minSdk 24, targetSdk 34. Use Kotlin DSL (.kts).
- Root build.gradle.kts declares plugins com.android.application 8.4.0 and org.jetbrains.kotlin.android 1.9.24 with apply false.
- settings.gradle.kts must declare google() and mavenCentral() in pluginManagement and dependencyResolutionManagement, and include(":app").
- app/build.gradle.kts must contain: packaging { resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"; excludes += "META-INF/DEPENDENCIES"; excludes += "META-INF/LICENSE"; excludes += "META-INF/NOTICE" } } (each exclude on its own line).
- Do NOT generate Gradle wrapper files or gradle.properties (they are added automatically).
- AndroidManifest.xml: MainActivity must have android:exported="true"; include the INTERNET permission; set android:icon to the icon resource described by the user message.
- For web projects (HTML/CSS/JS already placed in app/src/main/assets) create a minimal Kotlin Activity based on android.app.Activity (no AppCompat, no third-party dependencies) containing a WebView with JavaScript, DOM storage and file access enabled, loading file:///android_asset/ENTRY. Back button navigates WebView history. Use theme @android:style/Theme.Material.Light.NoActionBar.
- applicationId and namespace: com.zelsign.<app name lowercase letters and digits only>.
- Every file must be complete and compilable. No placeholders, no TODOs."""

    private const val HEAL_SYSTEM = """You are an expert Android/Gradle build doctor. A GitHub Actions build of an Android project failed.
Find the root cause from the logs and fix it by returning the COMPLETE new content of every file you need to change or add.
Return ONLY one JSON object, with no markdown fences and no prose, in exactly this shape:
{"summary":"what was wrong and what you changed","files":[{"path":"relative/path","content":"full new file text"}]}
Toolchain: JDK 17, Gradle 8.7, AGP 8.4.0, Kotlin 1.9.24, compileSdk 34. Keep the fix minimal. Never return partial files."""

    fun parse(text: String): AiResult {
        val s = text.indexOf('{')
        val e = text.lastIndexOf('}')
        if (s < 0 || e <= s) throw ApiException("AI did not return JSON: " + text.take(200))
        return try {
            ApiClient.gson.fromJson(text.substring(s, e + 1), AiResult::class.java)
                ?: throw ApiException("AI returned empty JSON")
        } catch (ex: ApiException) {
            throw ex
        } catch (ex: Exception) {
            throw ApiException("Could not parse AI JSON: ${ex.message}")
        }
    }

    /** Sanitised map path → content. */
    fun filesOf(r: AiResult): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (f in r.files.orEmpty()) {
            val p = f.path?.replace('\\', '/')?.trimStart('/') ?: continue
            val c = f.content ?: continue
            if (p.isBlank() || p.split("/").any { it == ".." }) continue
            out[p] = c.toByteArray(Charsets.UTF_8)
        }
        return out
    }

    suspend fun generate(
        cfg: AiConfig,
        appName: String,
        isWeb: Boolean,
        entry: String,
        missing: List<String>,
        files: Map<String, ByteArray>,
        hasIcon: Boolean,
        hasSplash: Boolean
    ): AiResult {
        val icon = if (hasIcon)
            "PROVIDED at app/src/main/res/mipmap-xxxhdpi/ic_launcher.png -> use @mipmap/ic_launcher in the manifest."
        else
            "NOT provided -> create a tasteful vector drawable app/src/main/res/drawable/ic_launcher.xml matching the app, and use @drawable/ic_launcher in the manifest."
        val splash = if (hasSplash)
            "PROVIDED at app/src/main/res/drawable/splash.png -> show it full-screen (ImageView, centerCrop) for about 1500 ms at launch before the main content."
        else
            "NOT provided -> create a vector drawable app/src/main/res/drawable/splash.xml (simple, on-brand) and show it full-screen for about 1500 ms at launch before the main content."
        val user = buildString {
            append("APP_NAME: ").append(appName.ifBlank { "(choose a good short name based on the project)" }).append('\n')
            append("PROJECT_TYPE: ").append(if (isWeb) "web (static files in app/src/main/assets)" else "android (partial)").append('\n')
            if (isWeb) append("WEB_ENTRY_FILE: ").append(entry).append('\n')
            append("MISSING_FILES: ").append(missing.joinToString(", ")).append('\n')
            append("ICON: ").append(icon).append('\n')
            append("SPLASH: ").append(splash).append('\n')
            append("\nFILE_LIST:\n").append(ProjectScanner.listing(files)).append('\n')
            val key = ProjectScanner.keyFileContents(files)
            if (key.isNotBlank()) append("\nEXISTING_KEY_FILES:\n").append(key)
        }
        return parse(ApiClient.chat(cfg, GEN_SYSTEM, user))
    }

    suspend fun heal(cfg: AiConfig, errors: String, files: Map<String, ByteArray>): AiResult {
        val sb = StringBuilder()
        var budget = 45000
        val ordered = files.keys.filter { ProjectScanner.isText(it) && !it.contains("/assets/") && !it.startsWith(".github/") }
            .sortedBy { if (ProjectScanner.isKeyFile(it)) 0 else 1 }
        for (p in ordered) {
            val t = String(files.getValue(p), Charsets.UTF_8).take(8000)
            if (t.length > budget) continue
            sb.append("--- ").append(p).append('\n').append(t).append('\n')
            budget -= t.length
        }
        val user = "BUILD_ERRORS:\n$errors\n\nFILE_LIST:\n${ProjectScanner.listing(files)}\n\nFILES:\n$sb"
        return parse(ApiClient.chat(cfg, HEAL_SYSTEM, user))
    }
}
