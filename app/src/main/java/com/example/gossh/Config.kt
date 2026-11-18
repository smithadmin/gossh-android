package com.example.gossh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Profile(
    val name: String = "",
    val protocol: String = "ssh", // "ssh" or "telnet"
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val keyFile: String = "",
    val theme: String = "",
)

data class ButtonDef(
    val label: String,
    val command: String,
    val enter: Boolean = true,
    val confirm: Boolean = false,
    val profile: String = "",
    val delayMs: Int = 0,
)

data class ColorScheme(
    val name: String,
    val bg: String = "",
    val fg: String = "",
    val accent: String = "",
) {
    val isDefault: Boolean get() = bg.isBlank() && fg.isBlank() && accent.isBlank()
}

data class Config(
    val profiles: List<Profile> = emptyList(),
    val buttons: List<ButtonDef> = defaultButtons(),
    val themes: List<ColorScheme> = emptyList(),
)

fun defaultButtons() = listOf(
    ButtonDef("uptime", "uptime"),
    ButtonDef("Disk", "df -h"),
    ButtonDef("Memory", "free -m"),
    ButtonDef("Top procs", "ps aux --sort=-%cpu | head -15"),
    ButtonDef("Grep log", "grep -i {{prompt:Pattern}} /var/log/syslog | tail -50"),
    ButtonDef("Ping", "ping -c 4 {{prompt:Host=8.8.8.8}}"),
    ButtonDef("Ctrl-C", "{ctrl-c}", enter = false),
    ButtonDef("Reboot", "sudo reboot", confirm = true),
)

private fun JSONArray?.objs(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

/** Same JSON layout as the desktop GoSSH config.json (key-file paths are device-local). */
object ConfigStore {
    private fun file(c: Context) = File(c.filesDir, "config.json")

    fun load(c: Context): Config {
        val f = file(c)
        if (!f.exists()) return Config()
        return try {
            val o = JSONObject(f.readText())
            Config(
                profiles = o.optJSONArray("profiles").objs().map {
                    Profile(
                        name = it.optString("name"),
                        protocol = it.optString("protocol").ifEmpty { "ssh" },
                        host = it.optString("host"),
                        port = it.optInt("port", 22),
                        user = it.optString("user"),
                        keyFile = it.optString("key_file"),
                        theme = it.optString("theme"),
                    )
                },
                buttons = if (o.has("buttons")) o.optJSONArray("buttons").objs().map {
                    ButtonDef(
                        label = it.optString("label"),
                        command = it.optString("command"),
                        enter = it.optBoolean("enter", true),
                        confirm = it.optBoolean("confirm", false),
                        profile = it.optString("profile"),
                        delayMs = it.optInt("delay_ms", 0),
                    )
                } else defaultButtons(),
                themes = o.optJSONArray("themes").objs().map {
                    ColorScheme(it.optString("name"), it.optString("bg"), it.optString("fg"), it.optString("accent"))
                },
            )
        } catch (e: Exception) {
            f.renameTo(File(f.path + ".bad")) // don't clobber a file we can't parse
            Config()
        }
    }

    fun save(c: Context, cfg: Config) {
        val o = JSONObject()
        o.put("profiles", JSONArray().also { a ->
            cfg.profiles.forEach { p ->
                a.put(
                    JSONObject().put("name", p.name).put("protocol", p.protocol).put("host", p.host)
                        .put("port", p.port).put("user", p.user).put("key_file", p.keyFile).put("theme", p.theme)
                )
            }
        })
        o.put("buttons", JSONArray().also { a ->
            cfg.buttons.forEach { b ->
                a.put(
                    JSONObject().put("label", b.label).put("command", b.command).put("enter", b.enter)
                        .put("confirm", b.confirm).put("profile", b.profile).put("delay_ms", b.delayMs)
                )
            }
        })
        o.put("themes", JSONArray().also { a ->
            cfg.themes.forEach { t ->
                a.put(JSONObject().put("name", t.name).put("bg", t.bg).put("fg", t.fg).put("accent", t.accent))
            }
        })
        file(c).writeText(o.toString(2))
    }
}
