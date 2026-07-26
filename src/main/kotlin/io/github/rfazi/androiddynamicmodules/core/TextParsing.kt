package io.github.rfazi.androiddynamicmodules.core

internal data class SourceLine(
    val content: String,
    val ending: String,
)

internal fun splitPreservingLineEndings(source: String): List<SourceLine> {
    if (source.isEmpty()) return listOf(SourceLine("", ""))

    val lines = mutableListOf<SourceLine>()
    var lineStart = 0
    var index = 0
    while (index < source.length) {
        when (source[index]) {
            '\r' -> {
                val ending = if (index + 1 < source.length && source[index + 1] == '\n') "\r\n" else "\r"
                lines += SourceLine(source.substring(lineStart, index), ending)
                index += ending.length
                lineStart = index
            }

            '\n' -> {
                lines += SourceLine(source.substring(lineStart, index), "\n")
                index++
                lineStart = index
            }

            else -> index++
        }
    }

    if (lineStart < source.length) {
        lines += SourceLine(source.substring(lineStart), "")
    }
    return lines
}

internal fun extractQuotedStrings(source: String): List<String> {
    val values = mutableListOf<String>()
    var index = 0
    var quote: Char? = null
    var escaped = false
    val current = StringBuilder()

    while (index < source.length) {
        val char = source[index]
        if (quote == null) {
            if (char == '/' && index + 1 < source.length && source[index + 1] == '/') {
                index = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                continue
            }
            if (char == '\'' || char == '"') {
                quote = char
                current.clear()
            }
        } else if (escaped) {
            current.append(char)
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == quote) {
            values += current.toString()
            quote = null
        } else {
            current.append(char)
        }
        index++
    }
    return values
}

internal fun balanceDelta(source: String): Int {
    var delta = 0
    var quote: Char? = null
    var escaped = false
    var index = 0
    while (index < source.length) {
        val char = source[index]
        if (quote == null) {
            if (char == '/' && index + 1 < source.length && source[index + 1] == '/') break
            when (char) {
                '\'', '"' -> quote = char
                '(', '[', '{' -> delta++
                ')', ']', '}' -> delta--
            }
        } else if (escaped) {
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == quote) {
            quote = null
        }
        index++
    }
    return delta
}

internal fun findTrailingComment(source: String): String? {
    var quote: Char? = null
    var escaped = false
    var index = 0
    while (index + 1 < source.length) {
        val char = source[index]
        if (quote == null) {
            if (char == '/' && source[index + 1] == '/') return source.substring(index).trimEnd()
            if (char == '\'' || char == '"') quote = char
        } else if (escaped) {
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == quote) {
            quote = null
        }
        index++
    }
    return null
}

internal fun renderWithReplacements(
    lines: List<SourceLine>,
    replacements: Map<IntRange, List<String>>,
): String {
    val result = StringBuilder()
    var lineIndex = 0
    val replacementsByStart = replacements.entries.associateBy { it.key.first }

    while (lineIndex < lines.size) {
        val replacement = replacementsByStart[lineIndex]
        if (replacement == null) {
            result.append(lines[lineIndex].content).append(lines[lineIndex].ending)
            lineIndex++
            continue
        }

        val lastOriginalLine = lines[replacement.key.last]
        replacement.value.forEachIndexed { replacementIndex, content ->
            result.append(content)
            if (replacementIndex < replacement.value.lastIndex) {
                result.append(lines[replacement.key.first].ending.ifEmpty { "\n" })
            } else {
                result.append(lastOriginalLine.ending)
            }
        }
        lineIndex = replacement.key.last + 1
    }
    return result.toString()
}
