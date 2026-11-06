package com.zelsigns.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zelsigns.app.AppViewModel
import com.zelsigns.app.Check
import com.zelsigns.app.Screen

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignZelApp(vm: AppViewModel) {
    val items = listOf(
        NavItem(Screen.SETTINGS, "Settings", Icons.Filled.Settings),
        NavItem(Screen.BUILDER, "Builder", Icons.Filled.Build),
        NavItem(Screen.PUSH, "GitHub", Icons.Filled.Send),
        NavItem(Screen.LOGS, "Logs", Icons.Filled.Info)
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SIGN ZEL", fontWeight = FontWeight.Black, letterSpacing = 3.sp) }
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    val selected = vm.screen == item.screen ||
                        (item.screen == Screen.BUILDER && vm.screen == Screen.PREVIEW)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.screen = item.screen },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.BUILDER -> BuilderScreen(vm)
                Screen.PREVIEW -> PreviewScreen(vm)
                Screen.PUSH -> PushScreen(vm)
                Screen.LOGS -> LogsScreen(vm)
            }
        }
    }
}

// ───────────────────────── shared bits ─────────────────────────

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CheckRow(label: String, check: Check, msg: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (check) {
            Check.OK -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E))
            Check.FAIL -> Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
            Check.RUNNING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Check.IDLE -> Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.outline)
        }
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (msg.isNotBlank()) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (check == Check.FAIL) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── Screen 1: Settings ─────────────────────────

@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Section("GitHub") {
            OutlinedTextField(
                value = vm.pat,
                onValueChange = { vm.pat = it; vm.save() },
                label = { Text("Personal Access Token (repo + workflow)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Section("AI provider") {
            OutlinedTextField(
                value = vm.aiKey,
                onValueChange = { vm.aiKey = it; vm.save() },
                label = { Text("AI API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.aiBaseUrl,
                onValueChange = { vm.aiBaseUrl = it; vm.save() },
                label = { Text("AI Base URL (OpenRouter / Groq / custom)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.aiModel,
                onValueChange = { vm.aiModel = it; vm.save() },
                label = { Text("AI Model Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    vm.aiBaseUrl = "https://openrouter.ai/api/v1"; vm.aiModel = "openai/gpt-4o-mini"; vm.save()
                }) { Text("OpenRouter") }
                TextButton(onClick = {
                    vm.aiBaseUrl = "https://api.groq.com/openai/v1"; vm.aiModel = "llama-3.3-70b-versatile"; vm.save()
                }) { Text("Groq") }
            }
        }
        Section("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(
                        selected = vm.themeMode == m,
                        onClick = { vm.themeMode = m; vm.save() },
                        label = { Text(m.label) }
                    )
                }
            }
        }
        Button(
            onClick = { vm.validate() },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.ghCheck != Check.RUNNING && vm.aiCheck != Check.RUNNING
        ) { Text("Validate Connections") }

        if (vm.ghCheck != Check.IDLE || vm.aiCheck != Check.IDLE) {
            Section("Results") {
                CheckRow("GitHub API (/user)", vm.ghCheck, vm.ghMsg)
                CheckRow("AI API (/chat/completions)", vm.aiCheck, vm.aiMsg)
            }
        }
        if (vm.ghCheck == Check.OK && vm.aiCheck == Check.OK) {
            Button(onClick = { vm.screen = Screen.BUILDER }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue to Builder")
            }
        }
    }
}

// ───────────────────────── Screen 2: Builder ─────────────────────────

@Composable
fun BuilderScreen(vm: AppViewModel) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.setSource(uri, false)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.setSource(uri, true)
    }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.iconUri = uri
    }
    val pickSplash = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.splashUri = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Section("1 · Project source") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        pickFile.launch(
                            arrayOf(
                                "application/zip", "application/x-zip-compressed",
                                "text/html", "application/octet-stream"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(".zip / .html") }
                OutlinedButton(onClick = { pickTree.launch(null) }, modifier = Modifier.weight(1f)) {
                    Text("Folder")
                }
            }
            Text(
                if (vm.sourceUri == null) "Nothing selected"
                else "Selected: ${vm.sourceName}" + if (vm.sourceIsTree) " (folder)" else "",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section("2 · Optional branding") {
            OutlinedTextField(
                value = vm.appName,
                onValueChange = { vm.appName = it },
                label = { Text("App name (blank = AI picks)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickIcon.launch(arrayOf("image/*")) }) { Text("Custom icon") }
                Text(
                    if (vm.iconUri == null) "AI will generate" else "Icon selected ✓",
                    style = MaterialTheme.typography.bodySmall
                )
                if (vm.iconUri != null) TextButton(onClick = { vm.iconUri = null }) { Text("Clear") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickSplash.launch(arrayOf("image/*")) }) { Text("Splash screen") }
                Text(
                    if (vm.splashUri == null) "AI will generate" else "Splash selected ✓",
                    style = MaterialTheme.typography.bodySmall
                )
                if (vm.splashUri != null) TextButton(onClick = { vm.splashUri = null }) { Text("Clear") }
            }
        }

        Button(
            onClick = { vm.analyze() },
            enabled = vm.sourceUri != null && !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze & Generate Missing Files") }

        if (vm.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
        }
        if (vm.errorMsg.isNotBlank()) {
            Text(vm.errorMsg, color = MaterialTheme.colorScheme.error)
        }
        if (vm.files.isNotEmpty() && !vm.busy) {
            OutlinedButton(onClick = { vm.screen = Screen.PREVIEW }, modifier = Modifier.fillMaxWidth()) {
                Text("View last preview (${vm.files.size} files)")
            }
        }
    }
}

// ───────────────────────── Preview ─────────────────────────

private data class TreeLine(val label: String, val path: String?)

private fun treeLines(paths: List<String>): List<TreeLine> {
    val out = mutableListOf<TreeLine>()
    val seen = mutableSetOf<String>()
    for (p in paths.sorted()) {
        val parts = p.split("/")
        for (i in 0 until parts.size - 1) {
            val dir = parts.take(i + 1).joinToString("/")
            if (seen.add(dir)) out.add(TreeLine("  ".repeat(i) + "📁 " + parts[i], null))
        }
        out.add(TreeLine("  ".repeat(parts.size - 1) + "📄 " + parts.last(), p))
    }
    return out
}

@Composable
fun PreviewScreen(vm: AppViewModel) {
    var open by remember { mutableStateOf<String?>(null) }
    val lines = remember(vm.files) { treeLines(vm.files.keys.toList()) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("AI summary") {
            Text(vm.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${vm.generated.size} generated / ${vm.files.size} total files · tap a green file to view it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Terminal.bg, RoundedCornerShape(10.dp)).padding(10.dp)
        ) {
            items(lines) { l ->
                val isGen = l.path != null && vm.generated.contains(l.path)
                Text(
                    l.label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isGen) Terminal.ok else Terminal.text,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isGen) { open = l.path }
                        .padding(vertical = 2.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.screen = Screen.BUILDER }, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = { vm.screen = Screen.PUSH }, modifier = Modifier.weight(2f)) {
                Text("Continue to GitHub")
            }
        }
    }

    val p = open
    if (p != null) {
        val text = vm.files[p]?.let { b ->
            if (ProjectViewHelper.isText(p)) String(b, Charsets.UTF_8).take(8000) else "(binary file, ${b.size} bytes)"
        } ?: ""
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            title = { Text(p, fontSize = 14.sp) },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        )
    }
}

private object ProjectViewHelper {
    fun isText(path: String) = com.zelsigns.app.data.ProjectScanner.isText(path)
}

// ───────────────────────── Screen 3: GitHub push ─────────────────────────

@Composable
fun PushScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Section("Repository") {
            OutlinedTextField(
                value = vm.repoName,
                onValueChange = { vm.repoName = it },
                label = { Text("Repo name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.privateRepo, onCheckedChange = { vm.privateRepo = it })
                Spacer(Modifier.size(10.dp))
                Text("Private repository")
            }
            Text(
                if (vm.files.isEmpty()) "No project analyzed yet – go to Builder first."
                else "${vm.files.size} files will be pushed (original + AI-generated) and the workflow will be triggered.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (vm.errorMsg.isNotBlank()) Text(vm.errorMsg, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { vm.pushAndBuild() },
            enabled = vm.files.isNotEmpty() && vm.repoName.isNotBlank() && !vm.building,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Push & Build") }
    }
}

// ───────────────────────── Screen 4: Logs ─────────────────────────

private fun logColor(line: String): Color = when {
    line.startsWith("✗") -> Terminal.error
    line.startsWith("✓") -> Terminal.ok
    line.startsWith("[AI]") -> Terminal.ai
    line.startsWith("$") -> Terminal.warn
    line.startsWith("  ") -> Terminal.dim
    else -> Terminal.text
}

@Composable
fun LogsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    LaunchedEffect(vm.logs.size) {
        if (vm.logs.isNotEmpty()) state.animateScrollToItem(vm.logs.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.building) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                if (vm.building) "Build loop running (Auto-Heal on)" else "Idle",
                style = MaterialTheme.typography.labelLarge
            )
        }
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Terminal.bg, RoundedCornerShape(10.dp)).padding(10.dp)
        ) {
            if (vm.logs.isEmpty()) {
                item { Text("$ waiting for a build…", color = Terminal.dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
            items(vm.logs) { line ->
                Text(
                    line,
                    color = logColor(line),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
        val url = vm.apkUrl
        if (url != null) {
            Button(
                onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$url#artifacts"))) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Download APK") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.building) {
                OutlinedButton(onClick = { vm.stopBuild() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
            val repo = vm.repoUrl
            if (repo != null) {
                OutlinedButton(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo))) },
                    modifier = Modifier.weight(1f)
                ) { Text("Open repo") }
            }
        }
    }
}
