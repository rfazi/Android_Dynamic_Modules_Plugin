package io.github.rfazi.androiddynamicmodules.core

private data class IncludeStatement(
    val lineRange: IntRange,
    val indentation: String,
    val disabled: Boolean,
    val modules: List<String>,
    val trailingComment: String?,
)

class GradleSettingsDocument private constructor(
    val source: String,
    val modules: List<ModuleState>,
    private val lines: List<SourceLine>,
    private val statements: List<IncludeStatement>,
) {
    fun render(moduleStates: Map<String, Boolean>): String {
        val normalizedStates = moduleStates.mapKeys { normalizeModulePath(it.key) }
        val replacements = statements.associate { statement ->
            statement.lineRange to statement.modules.mapIndexed { index, modulePath ->
                buildString {
                    append(statement.indentation)
                    val enabled = normalizedStates[modulePath] ?: !statement.disabled
                    if (!enabled) append("// ")
                    append("include(\"").append(modulePath).append("\")")
                    if (index == 0 && statement.trailingComment != null) {
                        append(' ').append(statement.trailingComment)
                    }
                }
            }
        }
        return renderWithReplacements(lines, replacements)
    }

    companion object {
        private val includeStart = Regex("""^([ \t]*)(//[ \t]*)?include\b(?!Build\b|Flat\b)(.*)$""")

        fun parse(source: String): GradleSettingsDocument {
            val lines = splitPreservingLineEndings(source)
            val statements = mutableListOf<IncludeStatement>()
            var lineIndex = 0

            while (lineIndex < lines.size) {
                val match = includeStart.matchEntire(lines[lineIndex].content)
                if (match == null) {
                    lineIndex++
                    continue
                }

                val indentation = match.groupValues[1]
                val disabled = match.groupValues[2].isNotEmpty()
                val firstLineBody = match.groupValues[3]
                var endLine = lineIndex
                var balance = balanceDelta(firstLineBody)
                val hasParenthesizedArguments = firstLineBody.contains('(')

                while (hasParenthesizedArguments && balance > 0 && endLine + 1 < lines.size) {
                    endLine++
                    balance += balanceDelta(lines[endLine].content)
                }

                if (hasParenthesizedArguments && balance != 0) {
                    throw IllegalArgumentException("Unbalanced include statement at line ${lineIndex + 1}")
                }

                val statementSource = buildString {
                    append("include").append(firstLineBody)
                    for (currentLine in (lineIndex + 1)..endLine) {
                        append('\n').append(lines[currentLine].content)
                    }
                }
                val modulePaths = extractQuotedStrings(statementSource)
                    .map(::normalizeModulePath)
                    .distinct()

                if (modulePaths.isNotEmpty()) {
                    statements += IncludeStatement(
                        lineRange = lineIndex..endLine,
                        indentation = indentation,
                        disabled = disabled,
                        modules = modulePaths,
                        trailingComment = findTrailingComment(firstLineBody),
                    )
                }
                lineIndex = endLine + 1
            }

            val moduleStates = linkedMapOf<String, Boolean>()
            statements.forEach { statement ->
                statement.modules.forEach { modulePath ->
                    moduleStates[modulePath] = moduleStates[modulePath] != false && !statement.disabled
                }
            }

            return GradleSettingsDocument(
                source = source,
                modules = moduleStates.map { (path, enabled) -> ModuleState(path, enabled) },
                lines = lines,
                statements = statements,
            )
        }
    }
}
