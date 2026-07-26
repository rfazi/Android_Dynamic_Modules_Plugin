package io.github.rfazi.androiddynamicmodules.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleSettingsDocumentTest {
    @Test
    fun `parses Kotlin and Groovy include forms without matching includeBuild`() {
        val source = """
            pluginManagement { includeBuild("build-logic") }
            include(":app", ":feature", ":feature-auth")
            // include ':optional' // local-only
            include ":legacy"
        """.trimIndent()

        val document = GradleSettingsDocument.parse(source)
        val modules = document.modules.associate { it.path to it.enabled }

        assertEquals(setOf(":app", ":feature", ":feature-auth", ":optional", ":legacy"), modules.keys)
        assertTrue(modules.getValue(":app"))
        assertFalse(modules.getValue(":optional"))
    }

    @Test
    fun `renders each module independently and preserves trailing comments`() {
        val source = """
            include(":app", ":feature", ":feature-auth")
            // include(":optional") // local-only
            includeBuild("build-logic")
        """.trimIndent() + "\n"
        val document = GradleSettingsDocument.parse(source)

        val rendered = document.render(
            mapOf(
                ":app" to true,
                ":feature" to false,
                ":feature-auth" to true,
                ":optional" to true,
            ),
        )

        assertEquals(
            """
                include(":app")
                // include(":feature")
                include(":feature-auth")
                include(":optional") // local-only
                includeBuild("build-logic")
            """.trimIndent() + "\n",
            rendered,
        )
    }

    @Test
    fun `supports multiline includes and CRLF line endings`() {
        val source = "include(\r\n    \":app\",\r\n    \":feature:login\",\r\n)\r\n"
        val document = GradleSettingsDocument.parse(source)

        val rendered = document.render(mapOf(":app" to true, ":feature:login" to false))

        assertEquals("include(\":app\")\r\n// include(\":feature:login\")\r\n", rendered)
    }
}
