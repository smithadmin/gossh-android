package com.example.gossh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import com.jcraft.jsch.Session as JSession

/** Protocol-independent interactive connection. All methods block; call off the main thread. */
abstract class Conn {
    abstract fun read(buf: ByteArray): Int // -1 at end of stream
    abstract fun write(data: ByteArray)
    abstract fun resize(rows: Int, cols: Int)
    abstract fun close()
    open fun scpUpload(ins: InputStream, remotePath: String, size: Long) { throw UnsupportedOperationException("Not supported") }
    open fun scpDownload(remotePath: String, outs: OutputStream) { throw UnsupportedOperationException("Not supported") }
}

// ---------------------------------------------------------------- SSH

class SshConn private constructor(
    private val session: JSession,
    private val channel: ChannelShell,
    private val ins: InputStream,
    private val outs: OutputStream,
) : Conn() {
    override fun read(buf: ByteArray): Int = ins.read(buf)
    override fun write(data: ByteArray) {
        outs.write(data)
        outs.flush()
    }

    override fun resize(rows: Int, cols: Int) {
        channel.setPtySize(cols, rows, 0, 0)
    }

    override fun scpUpload(ins: InputStream, remotePath: String, size: Long) {
        val ch = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        ch.connect(10_000)
        try {
            ins.use {
                ch.put(it, remotePath)
            }
        } finally {
            ch.disconnect()
        }
    }

    override fun scpDownload(remotePath: String, outs: OutputStream) {
        val ch = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        ch.connect(10_000)
        try {
            outs.use {
                ch.get(remotePath, it)
            }
        } finally {
            ch.disconnect()
        }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    /** Trust-on-first-use: unknown hosts are remembered, a changed key is refused. */
    private class TofuUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?): Boolean =
            message?.contains("CHANGED", ignoreCase = true) != true
        override fun showMessage(message: String?) {}
    }

    companion object {
        fun open(p: Profile, password: String, knownHosts: File, cols: Int, rows: Int): SshConn {
            val jsch = JSch()
            if (p.keyFile.isNotEmpty()) {
                // As on the desktop client, the password field doubles as the key passphrase.
                val data = File(p.keyFile).readBytes()
                jsch.addIdentity("key", data, null, if (password.isEmpty()) null else password.toByteArray())
            }
            if (!knownHosts.exists()) knownHosts.createNewFile()
            jsch.setKnownHosts(knownHosts.absolutePath)

            val s = jsch.getSession(p.user, p.host, p.port)
            if (password.isNotEmpty() && p.keyFile.isEmpty()) s.setPassword(password)
            s.setConfig("StrictHostKeyChecking", "ask")
            // Skip gssapi: its classes don't exist on Android.
            s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            s.userInfo = TofuUserInfo()
            s.setServerAliveInterval(30_000)
            try {
                s.connect(15_000)
                val ch = s.openChannel("shell") as ChannelShell
                ch.setPtyType("xterm-256color")
                ch.setPtySize(cols, rows, 0, 0)
                val ins = ch.inputStream // must be obtained before connect()
                val outs = ch.outputStream
                ch.connect(10_000)
                return SshConn(s, ch, ins, outs)
            } catch (e: Exception) {
                runCatching { s.disconnect() }
                throw e
            }
        }
    }
}

// ---------------------------------------------------------------- Telnet

/**
 * Minimal telnet client: strips protocol commands from the output, answers option
 * negotiation (accepts server echo + suppress-go-ahead, offers terminal type and
 * window size), escapes 0xFF on output and sends Enter as CR LF.
 */
class TelnetConn(host: String, port: Int) : Conn() {
    private val sock = Socket().also {
        it.connect(InetSocketAddress(host, port), 15_000)
        it.tcpNoDelay = true
        it.keepAlive = true
    }
    private val ins = sock.getInputStream()
    private val outs = sock.getOutputStream()
    private val raw = ByteArray(4096)

    // Parser state (only touched from read())
    private var state = 0 // 0 normal, 1 IAC, 2 option byte, 3 subnegotiation, 4 SB+IAC
    private var cmd = 0
    private val sb = ByteArrayOutputStream()
    private var afterCR = false

    private val lock = Any() // guards us, him, rows, cols
    private val us = HashSet<Int>()
    private val him = HashSet<Int>()
    private var rows = 24
    private var cols = 80
    private val wlock = Any() // serializes socket writes

    override fun read(buf: ByteArray): Int {
        if (buf.isEmpty()) return 0
        while (true) {
            val n = ins.read(raw, 0, minOf(buf.size, raw.size))
            if (n < 0) return -1
            var out = 0 // output never exceeds input, so buf is always big enough
            for (i in 0 until n) {
                val b = raw[i].toInt() and 0xFF
                when (state) {
                    0 -> {
                        if (b == IAC) {
                            state = 1
                            afterCR = false
                        } else if (afterCR && b == 0) { // CR NUL means bare CR
                            afterCR = false
                        } else {
                            afterCR = b == 13
                            buf[out++] = b.toByte()
                        }
                    }
                    1 -> when (b) {
                        IAC -> { buf[out++] = IAC.toByte(); state = 0 }
                        WILL, WONT, DO, DONT -> { cmd = b; state = 2 }
                        SB -> { sb.reset(); state = 3 }
                        else -> state = 0
                    }
                    2 -> { negotiate(cmd, b); state = 0 }
                    3 -> { if (b == IAC) state = 4 else sb.write(b) }
                    4 -> when (b) {
                        SE -> { subneg(sb.toByteArray()); state = 0 }
                        IAC -> { sb.write(IAC); state = 3 }
                        else -> state = 3
                    }
                }
            }
            if (out > 0) return out
        }
    }

    override fun write(data: ByteArray) {
        val o = ByteArrayOutputStream(data.size + 8)
        for (x in data) {
            val b = x.toInt() and 0xFF
            when (b) {
                IAC -> { o.write(IAC); o.write(IAC) }
                13 -> { o.write(13); o.write(10) }
                else -> o.write(b)
            }
        }
        send(o.toByteArray())
    }

    override fun resize(rows: Int, cols: Int) {
        synchronized(lock) {
            this.rows = rows
            this.cols = cols
            if (OPT_NAWS in us) sendNaws()
        }
    }

    override fun close() {
        runCatching { sock.close() }
    }

    private fun send(b: ByteArray) {
        synchronized(wlock) {
            outs.write(b)
            outs.flush()
        }
    }

    private fun negotiate(c: Int, opt: Int) {
        synchronized(lock) {
            fun reply(verb: Int) = send(byteArrayOf(IAC.toByte(), verb.toByte(), opt.toByte()))
            when (c) {
                WILL -> {
                    if (opt == OPT_ECHO || opt == OPT_SGA) {
                        if (him.add(opt)) reply(DO)
                    } else reply(DONT)
                }
                WONT -> { if (him.remove(opt)) reply(DONT) }
                DO -> {
                    if (opt == OPT_SGA || opt == OPT_TTYPE || opt == OPT_NAWS) {
                        if (us.add(opt)) {
                            reply(WILL)
                            if (opt == OPT_NAWS) sendNaws()
                        }
                    } else reply(WONT)
                }
                DONT -> { if (us.remove(opt)) reply(WONT) }
                else -> {}
            }
        }
    }

    private fun subneg(sb: ByteArray) {
        // IAC SB TTYPE SEND IAC SE  ->  IAC SB TTYPE IS "xterm-256color" IAC SE
        if (sb.size >= 2 && (sb[0].toInt() and 0xFF) == OPT_TTYPE && sb[1].toInt() == 1) {
            val m = ByteArrayOutputStream()
            m.write(IAC); m.write(SB); m.write(OPT_TTYPE); m.write(0)
            m.write("xterm-256color".toByteArray())
            m.write(IAC); m.write(SE)
            send(m.toByteArray())
        }
    }

    /** Report window size. Caller holds [lock]. */
    private fun sendNaws() {
        val m = ByteArrayOutputStream()
        m.write(IAC); m.write(SB); m.write(OPT_NAWS)
        for (v in intArrayOf(cols, rows)) {
            for (b in intArrayOf((v shr 8) and 0xFF, v and 0xFF)) {
                m.write(b)
                if (b == IAC) m.write(IAC)
            }
        }
        m.write(IAC); m.write(SE)
        send(m.toByteArray())
    }

    private companion object {
        const val IAC = 255
        const val DONT = 254
        const val DO = 253
        const val WONT = 252
        const val WILL = 251
        const val SB = 250
        const val SE = 240
        const val OPT_ECHO = 1
        const val OPT_SGA = 3
        const val OPT_TTYPE = 24
        const val OPT_NAWS = 31
    }
}
