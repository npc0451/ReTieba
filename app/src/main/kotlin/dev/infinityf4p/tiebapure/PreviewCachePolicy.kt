package dev.infinityf4p.tiebapure

internal const val PARENT_PREVIEW_BYTES = 12L * 1_024 * 1_024
/** Entries arrive oldest first; retain recent previews within both limits. */
internal fun retainedPreviewKeys(entries: List<Pair<String, Long>>): Set<String> {
    val retained = linkedMapOf<String, Long>()
    var bytes = 0L
    entries.forEach { (key, size) ->
        require(size >= 0)
        if (size > PARENT_PREVIEW_BYTES) return@forEach
        bytes -= retained.remove(key) ?: 0L
        retained[key] = size
        bytes += size
        while (retained.size > 4 || bytes > PARENT_PREVIEW_BYTES) {
            val oldest = retained.keys.first()
            bytes -= retained.remove(oldest)!!
        }
    }
    return retained.keys.toSet()
}
