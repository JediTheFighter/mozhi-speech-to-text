package com.mozhi.domain.transcription

/**
 * Merges overlapping Whisper windows so live UI does not stutter or repeat phrases.
 * Newest window is treated as the source of truth for the trailing partial.
 */
object TranscriptMerger {
    fun merge(committed: String, incoming: String): MergeResult {
        val previous = committed.trim()
        val next = incoming.trim()
        if (next.isEmpty()) return MergeResult(previous, "")
        if (previous.isEmpty()) return MergeResult("", next)

        val overlap = longestOverlap(previous, next)
        if (overlap.length >= minOverlapChars(previous, next)) {
            val suffix = next.removePrefix(overlap).trim()
            val stable = if (previous.endsWith(overlap)) previous else overlap
            return MergeResult(stable, suffix)
        }

        if (next.startsWith(previous)) {
            return MergeResult(previous, next.removePrefix(previous).trim())
        }

        if (previous.contains(next) && next.length > 8) {
            return MergeResult(previous, "")
        }

        return MergeResult(previous, next)
    }

    fun commitStablePrefix(committed: String, partial: String, minCommitChars: Int = 24): MergeResult {
        val merged = merge(committed, listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" "))
        val text = merged.display
        if (text.length < minCommitChars) return MergeResult(committed, partial)
        val splitAt = text.lastIndexOf(' ').takeIf { it >= minCommitChars } ?: return MergeResult(committed, partial)
        return MergeResult(text.substring(0, splitAt).trim(), text.substring(splitAt).trim())
    }

    private fun minOverlapChars(a: String, b: String): Int =
        minOf(12, minOf(a.length, b.length) / 3).coerceAtLeast(4)

    private fun longestOverlap(left: String, right: String): String {
        val max = minOf(left.length, right.length)
        for (len in max downTo 4) {
            val suffix = left.takeLast(len)
            if (right.startsWith(suffix)) return suffix
        }
        val leftTokens = left.split(WHITESPACE)
        val rightTokens = right.split(WHITESPACE)
        val maxTokens = minOf(leftTokens.size, rightTokens.size)
        for (count in maxTokens downTo 2) {
            val suffix = leftTokens.takeLast(count).joinToString(" ")
            if (right.startsWith(suffix)) return suffix
        }
        return ""
    }

    data class MergeResult(val committed: String, val partial: String) {
        val display: String
            get() = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
    }

    private val WHITESPACE = Regex("\\s+")
}
