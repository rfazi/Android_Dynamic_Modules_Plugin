package io.github.rfazi.androiddynamicmodules.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DynamicFeaturesDocumentTest {
    @Test
    fun `rewrites Groovy dynamic features`() {
        val source = """
            android {
                dynamicFeatures = [":df_search", ":df_payments"]
            }
        """.trimIndent() + "\n"
        val document = assertNotNull(DynamicFeaturesDocument.parse(Path.of("build.gradle"), source))

        val rendered = document.render(listOf(":df_payments"))

        assertEquals(
            """
                android {
                    dynamicFeatures = [":df_payments"]
                }
            """.trimIndent() + "\n",
            rendered,
        )
    }

    @Test
    fun `consolidates Kotlin dynamic feature declarations`() {
        val source = """
            android {
                dynamicFeatures += setOf(":df_search")
                dynamicFeatures += setOf(
                    ":df_payments",
                )
            }
        """.trimIndent() + "\n"
        val document = assertNotNull(DynamicFeaturesDocument.parse(Path.of("build.gradle.kts"), source))

        val rendered = document.render(listOf(":df_payments", ":df_search"))

        assertEquals(
            """
                android {
                    dynamicFeatures.clear()
                    dynamicFeatures += setOf(":df_payments", ":df_search")
                }
            """.trimIndent() + "\n",
            rendered,
        )
    }

    @Test
    fun `keeps an empty Kotlin declaration editable on the next run`() {
        val source = "android {\n    dynamicFeatures += setOf(\":df_search\")\n}\n"
        val firstPass = assertNotNull(DynamicFeaturesDocument.parse(Path.of("build.gradle.kts"), source))
            .render(emptyList())
        val secondPass = assertNotNull(DynamicFeaturesDocument.parse(Path.of("build.gradle.kts"), firstPass))
            .render(listOf(":df_search"))

        assertEquals(
            "android {\n    dynamicFeatures.clear()\n    dynamicFeatures += setOf(\":df_search\")\n}\n",
            secondPass,
        )
    }
}
