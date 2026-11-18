package com.example.gossh

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android ships a cut-down "BC"; swap in the full one so ed25519 etc. work.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        AppStore.init(this)
    }
}

/** Process-wide state: saved config and the open sessions (they outlive the Activity). */
object AppStore {
    lateinit var app: Application
    var config by mutableStateOf(Config())
        private set
    val sessions = mutableStateListOf<TermSession>()
    var selectedId by mutableStateOf(-1)
    val lastVals = HashMap<String, String>() // last non-secret prompt answers
    private var nextId = 1

    val selected: TermSession? get() = sessions.firstOrNull { it.id == selectedId }

    fun init(a: Application) {
        app = a
        config = ConfigStore.load(a)
    }

    fun update(f: (Config) -> Config) {
        config = f(config)
        ConfigStore.save(app, config)
    }

    fun knownHosts() = File(app.filesDir, "known_hosts")

    fun schemeByName(name: String): ColorScheme =
        config.themes.firstOrNull { it.name == name }
            ?: builtinSchemes.firstOrNull { it.name == name }
            ?: ColorScheme("Default")

    fun schemeNames(): List<String> =
        (builtinSchemes.map { it.name } + config.themes.map { it.name }).distinct()

    fun open(p: Profile, password: String) {
        val s = TermSession(nextId++, p, schemeByName(p.theme), app)
        sessions.add(s)
        selectedId = s.id
        SessionService.start(app)
        s.connect(password)
    }

    fun close(s: TermSession) {
        val i = sessions.indexOf(s)
        sessions.remove(s)
        s.close()
        if (selectedId == s.id) {
            selectedId = sessions.getOrNull(minOf(i, sessions.lastIndex))?.id ?: -1
        }
        sessionEnded()
    }

    /** Stop the foreground service once nothing is connected or connecting. */
    fun sessionEnded() {
        if (sessions.none { !it.ended }) SessionService.stop(app)
    }

    /** Copy a private key picked with the system file chooser into app-private storage. */
    fun importKey(uri: Uri): String? = try {
        val dir = File(app.filesDir, "keys").apply { mkdirs() }
        var name = "key"
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        val f = File(dir, "${System.currentTimeMillis()}-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        app.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.setReadable(false, false)
        f.setReadable(true, true)
        f.absolutePath
    } catch (e: Exception) {
        null
    }
}
