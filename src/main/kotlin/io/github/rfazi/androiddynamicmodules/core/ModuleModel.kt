package io.github.rfazi.androiddynamicmodules.core

import java.nio.file.Path

data class ModuleState(
    val path: String,
    val enabled: Boolean,
)

data class ModuleConfiguration(
    val applicationModule: String = ":app",
    val applicationModuleDirectory: String? = null,
    val requiredModules: Set<String> = setOf(":app"),
    val hiddenModules: Set<String> = emptySet(),
    val dynamicFeaturePrefixes: Set<String> = setOf("df_"),
) {
    fun isDynamicFeature(modulePath: String): Boolean {
        val name = normalizeModulePath(modulePath).substringAfterLast(':')
        return dynamicFeaturePrefixes.any(name::startsWith)
    }
}

data class ProjectModulesSnapshot(
    val projectRoot: Path,
    val settingsPath: Path,
    val settingsDocument: GradleSettingsDocument,
    val configuration: ModuleConfiguration,
    val applicationBuildPath: Path?,
    val dynamicFeaturesDocument: DynamicFeaturesDocument?,
) {
    val modules: List<ModuleState> = settingsDocument.modules

    val dynamicFeatureModules: Set<String> = buildSet {
        addAll(dynamicFeaturesDocument?.features.orEmpty())
        modules.map { it.path }.filterTo(this, configuration::isDynamicFeature)
    }
}

data class TextChange(
    val path: Path,
    val original: String,
    val updated: String,
)

data class ModuleChangePlan(
    val changes: List<TextChange>,
    val warnings: List<String> = emptyList(),
)

fun normalizeModulePath(rawPath: String): String {
    val segments = rawPath.trim().split(':').filter(String::isNotBlank)
    require(segments.isNotEmpty()) { "Invalid empty Gradle module path" }
    require(segments.all { segment ->
        segment != "." && segment != ".." && '/' !in segment && '\\' !in segment
    }) { "Invalid Gradle module path: $rawPath" }
    return segments.joinToString(separator = ":", prefix = ":")
}
