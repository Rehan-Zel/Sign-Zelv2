package com.zelsigns.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

object Templates {
    const val WORKFLOW_PATH = ".github/workflows/build-apk.yml"

    val WORKFLOW = """
name: Build APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle 8.7
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'

      - name: Build debug APK
        run: gradle assembleDebug --stacktrace --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
""".trimStart()

    val GRADLE_PROPERTIES = """
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
""".trimStart()
}

object ProjectReader {
    private val SKIP_DIRS = setOf("__MACOSX", ".git", "node_modules", ".gradle", ".idea", "build")

    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment ?: "file"
    }

    fun treeName(ctx: Context, uri: Uri): String =
        DocumentFile.fromTreeUri(ctx, uri)?.name ?: "folder"

    private fun clean(name: String): String? {
        val p = name.replace('\\', '/').trimStart('/')
        if (p.isBlank()) return null
        val parts = p.split("/")
        if (parts.any { it == ".." }) return null
        if (parts.any { it in SKIP_DIRS && it != parts.last() }) return null
        if (parts.last() == ".DS_Store") return null
        return p
    }

    /** Reads a .zip (unzipped in memory) or a single .html file. */
    fun readFile(ctx: Context, uri: Uri): Map<String, ByteArray> {
        val name = displayName(ctx, uri)
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open $name")
        val out = LinkedHashMap<String, ByteArray>()
        val looksZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (name.endsWith(".zip", ignoreCase = true) || looksZip) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val p = clean(e.name)
                        val data = z.readBytes()
                        if (p != null) out[p] = data
                    }
                    e = z.nextEntry
                }
            }
        } else {
            out[name] = bytes
        }
        return stripRoot(out)
    }

    /** Reads a whole folder picked with OpenDocumentTree. */
    fun readTree(ctx: Context, uri: Uri): Map<String, ByteArray> {
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: throw IOException("Cannot open folder")
        val out = LinkedHashMap<String, ByteArray>()
        walk(ctx, root, "", out)
        return stripRoot(out)
    }

    private fun walk(ctx: Context, dir: DocumentFile, prefix: String, out: MutableMap<String, ByteArray>) {
        for (f in dir.listFiles()) {
            val n = f.name ?: continue
            if (f.isDirectory) {
                if (n in SKIP_DIRS) continue
                walk(ctx, f, "$prefix$n/", out)
            } else {
                if (n == ".DS_Store") continue
                ctx.contentResolver.openInputStream(f.uri)?.use { out["$prefix$n"] = it.readBytes() }
            }
        }
    }

    /** If every file lives in one top-level directory, strip it. */
    private fun stripRoot(files: Map<String, ByteArray>): Map<String, ByteArray> {
        var cur = files
        while (cur.isNotEmpty() && cur.keys.all { it.contains('/') }) {
            val first = cur.keys.first().substringBefore('/')
            if (cur.keys.all { it.substringBefore('/') == first }) {
                cur = cur.mapKeys { it.key.substringAfter('/') }
            } else break
        }
        return cur
    }

    /** Decodes any image, scales it down and re-encodes as PNG. */
    fun toPng(ctx: Context, uri: Uri, maxDim: Int, square: Boolean): ByteArray {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("Cannot decode image")
        val scaled: Bitmap = if (square) {
            Bitmap.createScaledBitmap(bmp, maxDim, maxDim, true)
        } else {
            val r = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            if (r < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true) else bmp
        }
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}

object ProjectScanner {
    private val TEXT_EXT = setOf(
        "kt", "kts", "java", "gradle", "xml", "properties", "json", "html", "css", "js",
        "txt", "md", "yml", "yaml", "pro", "cfg", "svg", "toml"
    )

    fun isText(path: String) = path.substringAfterLast('.', "").lowercase() in TEXT_EXT

    fun isKeyFile(path: String): Boolean {
        val n = path.substringAfterLast('/')
        return n.endsWith(".gradle") || n.endsWith(".gradle.kts") || n == "AndroidManifest.xml" ||
            n == "gradle.properties"
    }

    fun hasAndroid(files: Map<String, ByteArray>): Boolean =
        files.keys.any { it.endsWith("AndroidManifest.xml") || it.endsWith(".gradle") || it.endsWith(".gradle.kts") }

    fun missing(files: Map<String, ByteArray>): List<String> {
        val k = files.keys
        val m = mutableListOf<String>()
        if (k.none { it == "settings.gradle" || it == "settings.gradle.kts" }) m.add("settings.gradle.kts")
        if (k.none { it == "build.gradle" || it == "build.gradle.kts" }) m.add("build.gradle.kts")
        if (k.none { it == "app/build.gradle" || it == "app/build.gradle.kts" }) m.add("app/build.gradle.kts")
        if (k.none { it.endsWith("AndroidManifest.xml") }) m.add("app/src/main/AndroidManifest.xml")
        if (k.none { it.endsWith(".kt") || it.endsWith(".java") }) m.add("app/src/main/java/.../MainActivity.kt")
        return m
    }

    /** Moves a plain web project into app/src/main/assets. */
    fun relocateWeb(files: Map<String, ByteArray>): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for ((p, b) in files) out["app/src/main/assets/$p"] = b
        return out
    }

    fun webEntry(assets: Map<String, ByteArray>): String {
        val prefix = "app/src/main/assets/"
        val htmls = assets.keys.filter { it.endsWith(".html", true) || it.endsWith(".htm", true) }
            .map { it.removePrefix(prefix) }
        if (htmls.contains("index.html")) return "index.html"
        return htmls.minByOrNull { it.count { c -> c == '/' } } ?: "index.html"
    }

    fun listing(files: Map<String, ByteArray>, max: Int = 400): String =
        files.keys.sorted().take(max).joinToString("\n")

    fun keyFileContents(files: Map<String, ByteArray>): String {
        val sb = StringBuilder()
        for ((p, b) in files) {
            if (isKeyFile(p) && b.size < 6000) sb.append("--- ").append(p).append('\n')
                .append(String(b, Charsets.UTF_8)).append('\n')
        }
        return sb.toString()
    }

    fun slug(s: String): String {
        val r = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (r.isBlank()) "app" else r.take(40)
    }
}
