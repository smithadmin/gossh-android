package com.example.gossh

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/**
 * One tab: a connection plus the WebView (xterm.js) that renders it.
 * Remote output -> base64 -> JS; keystrokes -> JS bridge -> single-threaded writer.
 */
class TermSession(val id: Int, val profile: Profile, scheme0: ColorScheme, ctx: Context) {
    val title: String = profile.name.ifEmpty { profile.host }
    val proto: String = profile.protocol.uppercase()
    val target: String =
        if (profile.protocol == "telnet") "${profile.host}:${profile.port}" else "${profile.user}@${profile.host}"

    var scheme by mutableStateOf(scheme0)
        private set
    var alive by mutableStateOf(false)
        private set
    var ended by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ctrl by mutableStateOf(false) // sticky Ctrl from the extra-keys row

    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var conn: Conn? = null
    @Volatile private var cols = 0
    @Volatile private var rows = 0
    @Volatile private var closing = false
    private val lock = Any()
    private var ready = false
    private val pending = ArrayList<String>()
    private var fontSize = 14

    val webView: WebView = createWebView(ctx.applicationContext)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(app: Context): WebView {
        val wv = WebView(MutableContextWrapper(app))
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.setBackgroundColor(android.graphics.Color.BLACK)
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        wv.isVerticalScrollBarEnabled = false
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.loadUrl("file:///android_asset/term.html")
        return wv
    }

    /** Called from the AndroidView factory: re-parent the WebView under the current Activity. */
    fun attachTo(ctx: Context): WebView {
        (webView.parent as? ViewGroup)?.removeView(webView)
        (webView.context as? MutableContextWrapper)?.baseContext = ctx
        return webView
    }

    inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            val toFlush = synchronized(lock) {
                ready = true
                val l = ArrayList(pending)
                pending.clear()
                l
            }
            main.post {
                applyThemeJs()
                toFlush.forEach { post64(it) }
            }
        }

        @JavascriptInterface
        fun onData(d: String) {
            if (ctrl && d.length == 1) {
                val c = d[0].uppercaseChar().code
                if (c in 64..95) { // @ A-Z [ \ ] ^ _  ->  control codes
                    ctrl = false
                    send(byteArrayOf((c - 64).toByte()))
                    return
                }
            }
            send(d)
        }

        @JavascriptInterface
        fun onResize(c: Int, r: Int) {
            cols = c
            rows = r
            val cn = conn ?: return
            try {
                writer.execute { try { cn.resize(r, c) } catch (e: Exception) { } }
            } catch (e: RejectedExecutionException) { }
        }
    }

    // ----- output path

    private fun emit(b: ByteArray, n: Int) {
        val s = Base64.encodeToString(b, 0, n, Base64.NO_WRAP)
        synchronized(lock) {
            if (!ready) {
                pending.add(s)
                return
            }
        }
        main.post { post64(s) }
    }

    private fun emitText(t: String) {
        val b = t.toByteArray()
        emit(b, b.size)
    }

    private fun post64(s: String) {
        webView.evaluateJavascript("w('$s')", null)
    }

    // ----- connection

    fun connect(password: String) {
        thread(name = "conn-$id", isDaemon = true) {
            var c: Conn? = null
            try {
                c = if (profile.protocol == "telnet") {
                    TelnetConn(profile.host, profile.port)
                } else {
                    SshConn.open(
                        profile, password, AppStore.knownHosts(),
                        if (cols > 0) cols else 80, if (rows > 0) rows else 24,
                    )
                }
                if (closing) { c.close(); return@thread }
                conn = c
                if (cols > 0 && rows > 0) c.resize(rows, cols)
                main.post { alive = true }
                val buf = ByteArray(8192)
                while (true) {
                    val n = c.read(buf)
                    if (n < 0) break
                    emit(buf, n)
                }
            } catch (e: Exception) {
                if (c == null && !closing) {
                    main.post { error = e.message ?: e.javaClass.simpleName }
                }
            } finally {
                if (c != null && !closing) emitText("\r\n\u001b[33m[connection closed]\u001b[0m\r\n")
                c?.close()
                main.post {
                    alive = false
                    ended = true
                    AppStore.sessionEnded()
                }
            }
        }
    }

    /** Close the connection but keep the tab (and its scrollback). */
    fun disconnect() {
        thread(isDaemon = true) { conn?.close() }
    }

    /** Tear everything down (tab closed). */
    fun close() {
        closing = true
        thread(isDaemon = true) { conn?.close() }
        writer.shutdown()
        main.post {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    fun scpUpload(ins: java.io.InputStream, remotePath: String, size: Long, onDone: (Result<Unit>) -> Unit) {
        val c = conn
        if (c == null) {
            onDone(Result.failure(Exception("Not connected")))
            return
        }
        thread(name = "scp-upload-$id", isDaemon = true) {
            val r = runCatching { c.scpUpload(ins, remotePath, size) }
            main.post { onDone(r) }
        }
    }

    fun scpDownload(remotePath: String, outs: java.io.OutputStream, onDone: (Result<Unit>) -> Unit) {
        val c = conn
        if (c == null) {
            onDone(Result.failure(Exception("Not connected")))
            return
        }
        thread(name = "scp-download-$id", isDaemon = true) {
            val r = runCatching { c.scpDownload(remotePath, outs) }
            main.post { onDone(r) }
        }
    }

    // ----- input path

    fun send(data: ByteArray) {
        try {
            writer.execute { try { conn?.write(data) } catch (e: Exception) { } }
        } catch (e: RejectedExecutionException) { }
    }

    fun send(text: String) = send(text.toByteArray(Charsets.UTF_8))

    fun paste(text: String) = send(text.replace("\r\n", "\r").replace("\n", "\r"))

    fun runButton(b: ButtonDef, vals: Map<String, String>) {
        if (!alive) return
        val script = render(expandTokens(b.command), vals, false).replace("\r\n", "\n").trimEnd('\n')
        val delay = b.delayMs.toLong()
        thread(isDaemon = true) {
            val lines = script.split("\n")
            for ((i, line) in lines.withIndex()) {
                val last = i == lines.lastIndex
                send(if (!last || b.enter) line + "\r" else line)
                if (!last && delay > 0) Thread.sleep(delay)
            }
        }
    }

    // ----- look

    fun applyColorScheme(sc: ColorScheme) {
        scheme = sc
        applyThemeJs()
    }

    private fun applyThemeJs() {
        val bg = normalizeHex(scheme.bg)
        val fg = normalizeHex(scheme.fg)
        val ac = normalizeHex(scheme.accent)
        val js = if (!scheme.isDefault && bg != null && fg != null && ac != null)
            "setTheme('$bg','$fg','$ac')" else "setTheme('','','')"
        webView.evaluateJavascript(js, null)
    }

    fun changeFont(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(8, 32)
        webView.evaluateJavascript("setFont($fontSize)", null)
    }

    fun showKeyboard() {
        webView.requestFocus()
        webView.evaluateJavascript("term.focus()", null)
        val imm = webView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
    }
}
