package io.github.rfazi.androiddynamicmodules.core

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleProjectServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plans settings and Kotlin dynamic feature changes without touching disk`() {
        val root = temporaryFolder.newFolder("project").toPath()
        val settings = root.resolve("settings.gradle.kts")
        val appBuild = root.resolve("app/build.gradle.kts")
        Files.createDirectories(appBuild.parent)
        Files.writeString(settings, "include(\":app\", \":optional\", \":df_search\")\n")
        Files.writeString(
            appBuild,
            "android {\n    dynamicFeatures += setOf(\":df_search\")\n}\n",
        )

        val snapshot = ModuleProjectService.load(root)
        val plan = ModuleProjectService.plan(
            snapshot,
            mapOf(":app" to false, ":optional" to false, ":df_search" to false),
        )

        assertEquals(2, plan.changes.size)
        assertTrue(plan.changes.first { it.path == settings }.updated.contains("include(\":app\")"))
        assertTrue(plan.changes.first { it.path == settings }.updated.contains("// include(\":optional\")"))
        assertTrue(plan.changes.first { it.path == appBuild }.updated.contains("dynamicFeatures.clear()"))
        assertEquals("include(\":app\", \":optional\", \":df_search\")\n", Files.readString(settings))
    }

    @Test
    fun `fails closed when dynamic features exist without an application build file`() {
        val root = temporaryFolder.newFolder("missing-app").toPath()
        val settings = root.resolve("settings.gradle.kts")
        Files.writeString(settings, "include(\":app\", \":df_search\")\n")
        val snapshot = ModuleProjectService.load(root)

        assertFailsWith<IllegalArgumentException> {
            ModuleProjectService.plan(snapshot, mapOf(":df_search" to false))
        }
        assertEquals("include(\":app\", \":df_search\")\n", Files.readString(settings))
    }

    @Test
    fun `loads project configuration and keeps required modules enabled`() {
        val root = temporaryFolder.newFolder("configured").toPath()
        Files.writeString(root.resolve("settings.gradle"), "include ':base', ':optional'\n")
        Files.writeString(
            root.resolve(ModuleConfigurationLoader.FILE_NAME),
            "applicationModule=:base\nrequiredModules=:base\nhiddenModules=:optional\ndynamicFeaturePrefixes=feature_\n",
        )

        val snapshot = ModuleProjectService.load(root)
        val plan = ModuleProjectService.plan(snapshot, mapOf(":base" to false, ":optional" to true))
        val updatedSettings = plan.changes.single().updated

        assertTrue(updatedSettings.contains("include(\":base\")"))
        assertTrue(updatedSettings.contains("include(\":optional\")"))
        assertEquals(setOf(":optional"), snapshot.configuration.hiddenModules)
    }
}
