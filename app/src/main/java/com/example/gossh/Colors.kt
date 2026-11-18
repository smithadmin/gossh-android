package com.example.gossh

import androidx.compose.ui.graphics.Color

val builtinSchemes = listOf(
    ColorScheme("Default"),
    ColorScheme("Dark", "#1e1e1e", "#d4d4d4", "#569cd6"),
    ColorScheme("Light", "#fdf6e3", "#073642", "#268bd2"),
    ColorScheme("Solarized Dark", "#002b36", "#93a1a1", "#2aa198"),
    ColorScheme("Green on Black", "#000000", "#33ff33", "#22aa22"),
    ColorScheme("Amber", "#1a1000", "#ffb000", "#ff8c00"),
    ColorScheme("Production (red)", "#2a0a0a", "#f0d8d8", "#e03030"),
    ColorScheme("Staging (yellow)", "#262200", "#f0ecc0", "#e0c020"),
    ColorScheme("Dev (green)", "#0a1f0a", "#d0f0d0", "#30b030"),
    ColorScheme("Ocean (blue)", "#0a1626", "#cfe3f5", "#3080e0"),
    ColorScheme("Purple", "#1a0f26", "#e6d8f5", "#9b59d0"),
)

/** "#abc" / "abc" / "#aabbcc" -> "#aabbcc", or null if invalid. */
fun normalizeHex(s: String): String? {
    var t = s.trim().removePrefix("#")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length != 6 || !t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + t.lowercase()
}

fun hexColor(h: String): Color? = normalizeHex(h)?.let {
    val v = it.substring(1).toInt(16)
    Color((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
}

fun contrastOn(c: Color): Color =
    if (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.58f) Color.Black else Color.White

/** Accent color if this scheme is usable (non-default, all three colors valid). */
fun ColorScheme.accentColor(): Color? =
    if (isDefault || hexColor(bg) == null || hexColor(fg) == null) null else hexColor(accent)
