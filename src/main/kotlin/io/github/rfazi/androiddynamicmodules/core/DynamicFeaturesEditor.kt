package io.github.rfazi.androiddynamicmodules.core

import java.nio.file.Path

private data class DynamicFeaturesStatement(
    val lineRange: IntRange,
    val indentation: String,
)

class DynamicFeaturesDocument private constructor(
    val source: String,
    val features: Set<String>,
    private val kotlinDsl: Boolean,
    private val lines: List<SourceLine>,
    private val statements: List<DynamicFeaturesStatement>,
) {
    fun render(enabledFeatures: Collection<String>): String {
        require(statements.isNotEmpty()) { "No dynamicFeatures declaration was found" }
        val normalizedFeatures = enabledFeatures.map(::normalizeModulePath).distinct().sorted()
        val firstStatement = statements.first()
        val replacementLines = if (kotlinDsl) {
            buildList {
                add("${firstStatement.indentation}dynamicFeatures.clear()")
                add(
                    if (normalizedFeatures.isEmpty()) {
                        "${firstStatement.indentation}dynamicFeatures += emptySet()"
                    } else {
                        "${firstStatement.indentation}dynamicFeatures += setOf(" +
                            normalizedFeatures.joinToString(", ") { "\"$it\"" } +
                            ")"
                    },
                )
            }
        } else {
            listOf(
                "${firstStatement.indentation}dynamicFeatures = [" +
                    normalizedFeatures.joinToString(", ") { "\"$it\"" } +
                    "]",
            )
        }

        val replacements = statements.associate { it.lineRange to emptyList<String>() }.toMutableMap()
        replacements[firstStatement.lineRange] = replacementLines
        return renderWithReplacements(lines, replacements)
    }

    companion object {
        private val statementStart = Regex(
            """^([ \t]*)(?:android\.)?dynamicFeatures\s*(?:\+=|=|\.(?:addAll|clear)\s*\().*$""",
        )

        fun parse(path: Path, source: String): DynamicFeaturesDocument? {
            val lines = splitPreservingLineEndings(source)
            val statements = mutableListOf<DynamicFeaturesStatement>()
            val featurePaths = linkedSetOf<String>()
            var lineIndex = 0

            while (lineIndex < lines.size) {
                val match = statementStart.matchEntire(lines[lineIndex].content)
                if (match == null) {
                    lineIndex++
                    continue
                }

                var endLine = lineIndex
                var balance = balanceDelta(lines[lineIndex].content)
                while (balance > 0 && endLine + 1 < lines.size) {
                    endLine++
                    balance += balanceDelta(lines[endLine].content)
                }
                if (balance != 0) {
                    throw IllegalArgumentException("Unbalanced dynamicFeatures declaration at line ${lineIndex + 1}")
                }

                val statementSource = (lineIndex..endLine).joinToString("\n") { lines[it].content }
                extractQuotedStrings(statementSource)
                    .mapTo(featurePaths, ::normalizeModulePath)
                statements += DynamicFeaturesStatement(lineIndex..endLine, match.groupValues[1])
                lineIndex = endLine + 1
            }

            if (statements.isEmpty()) return null
            return DynamicFeaturesDocument(
                source = source,
                features = featurePaths,
                kotlinDsl = path.fileName.toString().endsWith(".kts"),
                lines = lines,
                statements = statements,
            )
        }
    }
}
