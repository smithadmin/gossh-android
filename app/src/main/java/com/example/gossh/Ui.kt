package com.example.gossh

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.net.Uri
import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun Dropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("$label: $value", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

@Composable
fun MainScreen(nav: (String) -> Unit) {
    val s = AppStore.selected
    Column(Modifier.fillMaxSize()) {
        TabBar(nav)
        if (s != null) {
            Banner(s)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(s.id) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> s.attachTo(ctx) })
                }
                val err = s.error
                if (err != null) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Connection failed", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(err)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { AppStore.close(s) }) { Text("Close tab") }
                    }
                }
            }
            ButtonBar(s)
            ExtraKeys(s)
        }
    }
}

@Composable
fun TabBar(nav: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    
    // SCP Upload Flow
    var scpUploadUri by remember { mutableStateOf<Uri?>(null) }
    var scpUploadRemotePath by remember { mutableStateOf("") }
    
    val scpUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scpUploadUri = uri
            scpUploadRemotePath = "" // Ask user for path
        }
    }
    
    if (scpUploadUri != null) {
        AlertDialog(
            onDismissRequest = { scpUploadUri = null },
            title = { Text("SCP Upload") },
            text = {
                OutlinedTextField(
                    value = scpUploadRemotePath,
                    onValueChange = { scpUploadRemotePath = it },
                    label = { Text("Remote path") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = scpUploadUri!!
                    val remote = scpUploadRemotePath
                    scpUploadUri = null
                    
                    val session = AppStore.selected
                    if (session != null && remote.isNotEmpty()) {
                        Toast.makeText(ctx, "Starting upload...", Toast.LENGTH_SHORT).show()
                        try {
                            val ins = ctx.contentResolver.openInputStream(uri)
                            val size = ctx.contentResolver.query(uri, null, null, null, null)?.use {
                                if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE)) else 0L
                            } ?: 0L
                            
                            session.scpUpload(ins!!, remote, size) { res ->
                                Toast.makeText(ctx, if (res.isSuccess) "Upload complete" else "Upload failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Upload") }
            },
            dismissButton = { TextButton(onClick = { scpUploadUri = null }) { Text("Cancel") } }
        )
    }

    // SCP Download Flow
    var scpDownloadRemotePath by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    val scpDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            val session = AppStore.selected
            if (session != null && scpDownloadRemotePath.isNotEmpty()) {
                Toast.makeText(ctx, "Starting download...", Toast.LENGTH_SHORT).show()
                try {
                    val outs = ctx.contentResolver.openOutputStream(uri)
                    session.scpDownload(scpDownloadRemotePath, outs!!) { res ->
                        Toast.makeText(ctx, if (res.isSuccess) "Download complete" else "Download failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("SCP Download") },
            text = {
                OutlinedTextField(
                    value = scpDownloadRemotePath,
                    onValueChange = { scpDownloadRemotePath = it },
                    label = { Text("Remote path") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    val fileName = scpDownloadRemotePath.substringAfterLast('/')
                    scpDownloadLauncher.launch(fileName.ifEmpty { "downloaded_file" })
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") } }
        )
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            AppStore.sessions.forEach { t ->
                val sel = t.id == AppStore.selectedId
                val accent = t.scheme.accentColor()
                Row(
                    Modifier
                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { AppStore.selectedId = t.id }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (accent != null) {
                        Box(Modifier.size(10.dp).background(accent, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(t.title + if (t.ended) " (closed)" else "", maxLines = 1)
                    Text("  ×", Modifier.clickable { AppStore.close(t) })
                }
            }
        }
        TextButton(onClick = { nav("connect") }) { Text("+") }
        Box {
            TextButton(onClick = { menu = true }) { Text("⋮") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit buttons") }, onClick = { menu = false; nav("buttons") })
                DropdownMenuItem(text = { Text("Session colors") }, onClick = { menu = false; nav("colors") })
                DropdownMenuItem(text = { Text("SCP Upload") }, onClick = { menu = false; scpUploadLauncher.launch("*/*") })
                DropdownMenuItem(text = { Text("SCP Download") }, onClick = { menu = false; showDownloadDialog = true })
                DropdownMenuItem(text = { Text("Larger text") }, onClick = { menu = false; AppStore.selected?.changeFont(2) })
                DropdownMenuItem(text = { Text("Smaller text") }, onClick = { menu = false; AppStore.selected?.changeFont(-2) })
                DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; AppStore.selected?.disconnect() })
                DropdownMenuItem(text = { Text("Close tab") }, onClick = { menu = false; AppStore.selected?.let { AppStore.close(it) } })
            }
        }
    }
}

/** Accent-colored title bar above the terminal; hidden for the Default scheme. */
@Composable
fun Banner(s: TermSession) {
    val accent = s.scheme.accentColor() ?: return
    Box(Modifier.fillMaxWidth().background(accent).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(
            "${s.title}  -  ${s.target} [${s.proto}]" + if (s.ended) "  (closed)" else "",
            color = contrastOn(accent), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
        )
    }
}

@Composable
fun ButtonBar(s: TermSession) {
    val visible = AppStore.config.buttons.filter { it.profile.isEmpty() || it.profile == s.profile.name }
    var prompt by remember { mutableStateOf<ButtonDef?>(null) }
    var confirm by remember { mutableStateOf<Pair<ButtonDef, Map<String, String>>?>(null) }

    fun go(b: ButtonDef, vals: Map<String, String>) {
        if (b.confirm) confirm = b to vals else s.runButton(b, vals)
    }

    fun press(b: ButtonDef) {
        if (parseVars(b.command).isEmpty()) go(b, emptyMap()) else prompt = b
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        items(visible) { b ->
            Button(onClick = { press(b) }, enabled = s.alive) { Text(b.label, maxLines = 1) }
        }
    }

    prompt?.let { b ->
        PromptDialog(b, onCancel = { prompt = null }, onOk = { vals -> prompt = null; go(b, vals) })
    }
    confirm?.let { (b, vals) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Run \"${b.label}\"?") },
            text = { Text(render(b.command, vals, true)) },
            confirmButton = { TextButton(onClick = { confirm = null; s.runButton(b, vals) }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PromptDialog(b: ButtonDef, onCancel: () -> Unit, onOk: (Map<String, String>) -> Unit) {
    val vars = remember(b) { parseVars(b.command) }
    val vals = remember(b) {
        mutableStateMapOf<String, String>().apply {
            vars.forEach { v -> put(v.name, if (v.secret) "" else AppStore.lastVals[v.name] ?: v.default) }
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(b.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vars.forEach { v ->
                    OutlinedTextField(
                        value = vals[v.name] ?: "",
                        onValueChange = { vals[v.name] = it },
                        label = { Text(if (v.secret) "${v.name} (hidden)" else v.name) },
                        singleLine = true,
                        visualTransformation = if (v.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vars.filter { !it.secret }.forEach { AppStore.lastVals[it.name] = vals[it.name] ?: "" } // secrets never remembered
                onOk(vals.toMap())
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private fun clipboardText(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
}

/** Keys a phone keyboard lacks. Ctrl is sticky: tap it, then type a letter. */
@Composable
fun ExtraKeys(s: TermSession) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyBtn("Esc") { s.send("\u001b") }
        KeyBtn("Tab") { s.send("\t") }
        KeyBtn("Ctrl", s.ctrl) { s.ctrl = !s.ctrl }
        KeyBtn("↑") { s.send("\u001b[A") }
        KeyBtn("↓") { s.send("\u001b[B") }
        KeyBtn("←") { s.send("\u001b[D") }
        KeyBtn("→") { s.send("\u001b[C") }
        listOf("|", "~", "-", "/", "\\", "_").forEach { ch -> KeyBtn(ch) { s.send(ch) } }
        KeyBtn("Paste") { clipboardText(ctx)?.let { s.paste(it) } }
        KeyBtn("⌨") { s.showKeyboard() }
    }
}

@Composable
fun KeyBtn(label: String, active: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.height(34.dp)
    val pad = PaddingValues(horizontal = 10.dp)
    if (active) {
        Button(onClick = onClick, modifier = m, contentPadding = pad) { Text(label, fontSize = 13.sp) }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, contentPadding = pad) { Text(label, fontSize = 13.sp) }
    }
}
