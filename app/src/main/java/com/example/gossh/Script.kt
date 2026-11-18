package com.example.gossh

// {{prompt:Name}}  {{prompt:Name=default}}  {{secret:Name}}
val PLACEHOLDER = Regex("""\{\{(prompt|secret):([^=}]+)(?:=([^}]*))?\}\}""")

data class Var(val name: String, val default: String, val secret: Boolean)

/** Control-key tokens are expanded in the template before any prompt values go in. */
fun expandTokens(s: String): String = s
    .replace("{ctrl-c}", "\u0003")
    .replace("{ctrl-d}", "\u0004")
    .replace("{ctrl-z}", "\u001a")
    .replace("{esc}", "\u001b")
    .replace("{tab}", "\t")
    .replace("{enter}", "\r")

/** Distinct variables in order of first appearance (a repeated name is asked once). */
fun parseVars(tpl: String): List<Var> {
    val seen = HashSet<String>()
    val out = ArrayList<Var>()
    for (m in PLACEHOLDER.findAll(tpl)) {
        val name = m.groupValues[2].trim()
        if (!seen.add(name)) continue
        out.add(Var(name, m.groupValues[3], m.groupValues[1] == "secret"))
    }
    return out
}

/** Substitute values; with mask set, secrets show as bullets (for confirmation text). */
fun render(tpl: String, vals: Map<String, String>, mask: Boolean): String =
    PLACEHOLDER.replace(tpl) { m ->
        if (mask && m.groupValues[1] == "secret") "••••••"
        else vals[m.groupValues[2].trim()] ?: ""
    }
