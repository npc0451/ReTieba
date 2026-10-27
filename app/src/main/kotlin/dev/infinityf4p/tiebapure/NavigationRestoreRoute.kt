package dev.infinityf4p.tiebapure

import java.net.URLEncoder

/** Nullable query arguments must be omitted; Boolean navigation arguments are not Strings. */
internal fun navigationRestoreRoute(pattern: String?, arguments: Map<String, Any?>): String? {
    pattern ?: return null
    fun value(key: String): String? = when (val value = arguments[key]) {
        is String, is Boolean, is Int, is Long, is Float -> URLEncoder.encode(value.toString(), "UTF-8").replace("+", "%20")
        else -> null
    }
    val variable = Regex("\\{([^}]+)\\}")
    val parts = pattern.split('?', limit = 2)
    var valid = true
    val path = variable.replace(parts[0]) { match -> value(match.groupValues[1]) ?: "".also { valid = false } }
    if (!valid) return null
    val query = parts.getOrNull(1)?.split('&').orEmpty().mapNotNull { parameter ->
        if (variable.findAll(parameter).any { value(it.groupValues[1]) == null }) null
        else variable.replace(parameter) { value(it.groupValues[1]).orEmpty() }
    }
    return path + if (query.isEmpty()) "" else "?" + query.joinToString("&")
}
