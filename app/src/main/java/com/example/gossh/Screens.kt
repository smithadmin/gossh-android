package com.example.gossh

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// ------------------------------------------------------------------ connect

@Composable
fun ConnectScreen(canCancel: Boolean, onCancel: () -> Unit, onConnected: () -> Unit) {
    val ctx = LocalContext.current
    val cfg = AppStore.config
    var profileName by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf("ssh") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var keyFile by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("Default") }
    var saving by remember { mutableStateOf(false) }

    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val path = AppStore.importKey(uri)
            if (path != null) keyFile = path else Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show()
        }
    }

    fun setProto(p: String) {
        proto = p
        if (p == "telnet" && port == "22") port = "23"
        if (p == "ssh" && port == "23") port = "22"
    }

    fun formProfile(name: String) = Profile(
        name = name,
        protocol = proto,
        host = host.trim(),
        port = port.toIntOrNull() ?: if (proto == "telnet") 23 else 22,
        user = user.trim(),
        keyFile = keyFile,
        theme = if (scheme == "Default") "" else scheme,
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("New connection", style = MaterialTheme.typography.titleLarge)

        if (cfg.profiles.isNotEmpty()) {
            Dropdown("Saved", profileName.ifEmpty { "(choose)" }, cfg.profiles.map { it.name }) { n ->
                cfg.profiles.firstOrNull { it.name == n }?.let { p ->
                    profileName = p.name; proto = p.protocol; host = p.host; port = p.port.toString()
                    user = p.user; keyFile = p.keyFile
                    scheme = if (AppStore.schemeNames().contains(p.theme)) p.theme else "Default"
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (proto == "ssh") Button(onClick = { setProto("ssh") }) { Text("SSH") }
            else OutlinedButton(onClick = { setProto("ssh") }) { Text("SSH") }
            if (proto == "telnet") Button(onClick = { setProto("telnet") }) { Text("Telnet") }
            else OutlinedButton(onClick = { setProto("telnet") }) { Text("Telnet") }
        }

        OutlinedTextField(
            value = host, onValueChange = { host = it }, label = { Text("Host") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (proto == "ssh") {
            OutlinedTextField(
                value = user, onValueChange = { user = it }, label = { Text("User") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text("Import key...") }
                Text(
                    if (keyFile.isEmpty()) "No key (password / agent-less)" else File(keyFile).name.substringAfter('-'),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                )
                if (keyFile.isNotEmpty()) TextButton(onClick = { keyFile = "" }) { Text("Clear") }
            }
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Password / key passphrase (never saved)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Telnet has no automatic login: type the username and password in the terminal. Traffic is unencrypted.", fontSize = 12.sp)
        }

        Dropdown("Colors", scheme, AppStore.schemeNames()) { scheme = it }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (host.isBlank() || (proto == "ssh" && user.isBlank())) {
                    Toast.makeText(ctx, "Enter a host (and a user for SSH)", Toast.LENGTH_SHORT).show()
                } else {
                    AppStore.open(formProfile(profileName), pass)
                    pass = ""
                    onConnected()
                }
            }) { Text("Connect") }
            OutlinedButton(onClick = { if (host.isNotBlank()) saving = true }) { Text("Save session") }
            if (canCancel) OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        if (profileName.isNotEmpty()) {
            TextButton(onClick = {
                AppStore.update { c -> c.copy(profiles = c.profiles.filter { it.name != profileName }) }
                profileName = ""
            }) { Text("Delete saved session \"$profileName\"") }
        }
    }

    if (saving) {
        var name by remember { mutableStateOf(profileName.ifEmpty { host }) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save session") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        val p = formProfile(n)
                        AppStore.update { c -> c.copy(profiles = c.profiles.filter { it.name != n } + p) }
                        profileName = n
                    }
                    saving = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ button editor

@Composable
fun ButtonEditorScreen(onDone: () -> Unit) {
    val buttons = AppStore.config.buttons
    var editIdx by remember { mutableStateOf<Int?>(null) } // -1 = new

    fun move(i: Int, d: Int) = AppStore.update { c ->
        val l = c.buttons.toMutableList()
        val j = i + d
        if (j in l.indices) { val t = l[i]; l[i] = l[j]; l[j] = t }
        c.copy(buttons = l)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buttons", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { editIdx = -1 }) { Text("New") }
            OutlinedButton(onClick = onDone) { Text("Done") }
        }
        Text(
            "Control keys: {ctrl-c} {ctrl-d} {ctrl-z} {esc} {tab} {enter}\n" +
                "Ask on tap: {{prompt:Name}}  {{prompt:Name=default}}  {{secret:Name}} (hidden, never remembered)",
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(buttons) { i, b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editIdx = i }) {
                        Text(b.label, fontWeight = FontWeight.Bold)
                        Text(b.command.replace("\n", " ⏎ "), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                    TextButton(onClick = { move(i, -1) }) { Text("↑") }
                    TextButton(onClick = { move(i, 1) }) { Text("↓") }
                    TextButton(onClick = {
                        AppStore.update { c -> c.copy(buttons = c.buttons.filterIndexed { k, _ -> k != i }) }
                    }) { Text("✕") }
                }
            }
        }
    }

    editIdx?.let { idx ->
        ButtonEditDialog(
            initial = if (idx >= 0 && idx < buttons.size) buttons[idx] else ButtonDef("", ""),
            onCancel = { editIdx = null },
            onSave = { nb ->
                AppStore.update { c ->
                    if (idx >= 0 && idx < c.buttons.size) c.copy(buttons = c.buttons.toMutableList().also { it[idx] = nb })
                    else c.copy(buttons = c.buttons + nb)
                }
                editIdx = null
            },
        )
    }
}

@Composable
fun ButtonEditDialog(initial: ButtonDef, onCancel: () -> Unit, onSave: (ButtonDef) -> Unit) {
    var label by remember { mutableStateOf(initial.label) }
    var command by remember { mutableStateOf(initial.command) }
    var enter by remember { mutableStateOf(initial.enter) }
    var confirm by remember { mutableStateOf(initial.confirm) }
    var prof by remember { mutableStateOf(initial.profile) }
    var delay by remember { mutableStateOf(if (initial.delayMs > 0) initial.delayMs.toString() else "") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial.label.isEmpty()) "New button" else "Edit button") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(
                    value = command, onValueChange = { command = it }, label = { Text("Command") },
                    minLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enter, onCheckedChange = { enter = it })
                    Text("Press Enter after the command")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirm, onCheckedChange = { confirm = it })
                    Text("Ask before sending")
                }
                OutlinedTextField(
                    value = prof, onValueChange = { prof = it }, singleLine = true,
                    label = { Text("Only for saved session (blank = all)") },
                )
                OutlinedTextField(
                    value = delay, onValueChange = { delay = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text("Delay between lines (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) onSave(ButtonDef(label.trim(), command, enter, confirm, prof.trim(), delay.toIntOrNull() ?: 0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ colors

@Composable
fun HexField(label: String, value: String, onChange: (String) -> Unit) {
    val c = hexColor(value)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            isError = value.isNotBlank() && c == null, modifier = Modifier.weight(1f),
            placeholder = { Text("#RRGGBB") },
        )
        Box(Modifier.size(40.dp).background(c ?: Color.Gray, CircleShape))
    }
}

/** Edits the selected tab's colors; every valid change applies immediately. */
@Composable
fun ColorEditorScreen(onDone: () -> Unit) {
    val s = AppStore.selected
    if (s == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    var preset by remember { mutableStateOf(s.scheme.name) }
    var bg by remember { mutableStateOf(s.scheme.bg) }
    var fg by remember { mutableStateOf(s.scheme.fg) }
    var ac by remember { mutableStateOf(s.scheme.accent) }
    var savingPreset by remember { mutableStateOf(false) }

    fun push(nbg: String, nfg: String, nac: String) {
        val b = normalizeHex(nbg); val f = normalizeHex(nfg); val a = normalizeHex(nac)
        if (nbg.isBlank() && nfg.isBlank() && nac.isBlank()) s.applyColorScheme(ColorScheme("Default"))
        else if (b != null && f != null && a != null) s.applyColorScheme(ColorScheme(preset, b, f, a))
        // otherwise incomplete/invalid: wait for the rest
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Colors - ${s.title}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onDone) { Text("Done") }
        }
        Dropdown("Preset", preset.ifEmpty { "Custom" }, AppStore.schemeNames()) { n ->
            val sc = AppStore.schemeByName(n)
            preset = n; bg = sc.bg; fg = sc.fg; ac = sc.accent
            push(bg, fg, ac)
        }
        HexField("Background", bg) { bg = it; push(bg, fg, ac) }
        HexField("Text", fg) { fg = it; push(bg, fg, ac) }
        HexField("Accent", ac) { ac = it; push(bg, fg, ac) }

        val pvBg = hexColor(bg); val pvFg = hexColor(fg); val pvAc = hexColor(ac)
        Column(Modifier.fillMaxWidth().background(pvBg ?: Color.Black)) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(pvAc ?: Color.Transparent))
            Text(
                "user@host:~$ ls -la", color = pvFg ?: Color.White, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
        Text(
            "Accent colors the banner, the tab dot and the cursor. Leave all three blank for the plain black terminal. " +
                "Programs that set their own colors (ls --color, vim themes) keep them.",
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (normalizeHex(bg) != null && normalizeHex(fg) != null && normalizeHex(ac) != null) savingPreset = true
            }) { Text("Save as preset...") }
            OutlinedButton(onClick = {
                AppStore.update { c -> c.copy(themes = c.themes.filter { it.name != preset }) }
            }) { Text("Delete preset") }
        }
    }

    if (savingPreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text("Save preset") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    val b = normalizeHex(bg); val f = normalizeHex(fg); val a = normalizeHex(ac)
                    if (n.isNotEmpty() && n != "Default" && b != null && f != null && a != null) {
                        val sc = ColorScheme(n, b, f, a)
                        AppStore.update { c -> c.copy(themes = c.themes.filter { it.name != n } + sc) }
                        preset = n
                    }
                    savingPreset = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { savingPreset = false }) { Text("Cancel") } },
        )
    }
}
