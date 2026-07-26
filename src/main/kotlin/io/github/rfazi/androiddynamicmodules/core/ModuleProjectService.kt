package io.github.rfazi.androiddynamicmodules.core

import java.nio.file.Files
import java.nio.file.Path

object ModuleProjectService {
    fun load(projectRoot: Path): ProjectModulesSnapshot {
        val normalizedRoot = projectRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedRoot)) { "Project directory does not exist: $normalizedRoot" }

        val settingsPath = resolveSingleFile(
            normalizedRoot.resolve("settings.gradle.kts"),
            normalizedRoot.resolve("settings.gradle"),
            "Gradle settings file",
            required = true,
        )!!
        val settingsSource = Files.readString(settingsPath)
        val settingsDocument = GradleSettingsDocument.parse(settingsSource)
        require(settingsDocument.modules.isNotEmpty()) {
            "No literal include(...) module declarations were found in ${settingsPath.fileName}"
        }

        val configuration = ModuleConfigurationLoader.load(normalizedRoot)
        val applicationDirectory = resolveApplicationDirectory(normalizedRoot, configuration)
        val applicationBuildPath = resolveSingleFile(
            applicationDirectory.resolve("build.gradle.kts"),
            applicationDirectory.resolve("build.gradle"),
            "application module build file",
            required = false,
        )
        val dynamicFeaturesDocument = applicationBuildPath?.let { path ->
            DynamicFeaturesDocument.parse(path, Files.readString(path))
        }

        return ProjectModulesSnapshot(
            projectRoot = normalizedRoot,
            settingsPath = settingsPath,
            settingsDocument = settingsDocument,
            configuration = configuration,
            applicationBuildPath = applicationBuildPath,
            dynamicFeaturesDocument = dynamicFeaturesDocument,
        )
    }

    fun plan(
        snapshot: ProjectModulesSnapshot,
        requestedStates: Map<String, Boolean>,
    ): ModuleChangePlan {
        val knownModules = snapshot.modules.mapTo(linkedSetOf()) { it.path }
        val unknownModules = requestedStates.keys.map(::normalizeModulePath).filterNot(knownModules::contains)
        require(unknownModules.isEmpty()) { "Unknown modules: ${unknownModules.joinToString()}" }

        val desiredStates = snapshot.modules.associate { module ->
            module.path to (requestedStates[module.path] ?: module.enabled)
        }.toMutableMap()
        snapshot.configuration.requiredModules.forEach { requiredModule ->
            if (requiredModule in knownModules) desiredStates[requiredModule] = true
        }

        val changes = mutableListOf<TextChange>()
        val updatedSettings = snapshot.settingsDocument.render(desiredStates)
        if (updatedSettings != snapshot.settingsDocument.source) {
            changes += TextChange(snapshot.settingsPath, snapshot.settingsDocument.source, updatedSettings)
        }

        val dynamicFeatureModules = snapshot.dynamicFeatureModules.intersect(knownModules)
        if (dynamicFeatureModules.isNotEmpty()) {
            val applicationBuildPath = requireNotNull(snapshot.applicationBuildPath) {
                "Dynamic feature modules were found, but the application build file does not exist"
            }
            val dynamicFeaturesDocument = requireNotNull(snapshot.dynamicFeaturesDocument) {
                "Dynamic feature modules were found, but no dynamicFeatures declaration exists in ${applicationBuildPath.fileName}"
            }
            val enabledDynamicFeatures = dynamicFeatureModules.filter { desiredStates[it] == true }
            val updatedApplicationBuild = dynamicFeaturesDocument.render(enabledDynamicFeatures)
            if (updatedApplicationBuild != dynamicFeaturesDocument.source) {
                changes += TextChange(
                    applicationBuildPath,
                    dynamicFeaturesDocument.source,
                    updatedApplicationBuild,
                )
            }
        }

        val warnings = buildList {
            val missingRequired = snapshot.configuration.requiredModules - knownModules
            if (missingRequired.isNotEmpty()) {
                add("Configured required modules were not found: ${missingRequired.joinToString()}")
            }
        }
        return ModuleChangePlan(changes = changes, warnings = warnings)
    }

    private fun resolveApplicationDirectory(
        projectRoot: Path,
        configuration: ModuleConfiguration,
    ): Path {
        val relativeDirectory = configuration.applicationModuleDirectory?.let(Path::of)
            ?: Path.of(
                configuration.applicationModule
                    .removePrefix(":")
                    .replace(':', '/'),
            )
        val resolved = projectRoot.resolve(relativeDirectory).normalize()
        require(resolved.startsWith(projectRoot)) {
            "applicationModuleDirectory must stay inside the project directory"
        }
        return resolved
    }

    private fun resolveSingleFile(
        first: Path,
        second: Path,
        label: String,
        required: Boolean,
    ): Path? {
        val existing = listOf(first, second).filter(Files::isRegularFile)
        require(existing.size <= 1) {
            "Both ${first.fileName} and ${second.fileName} exist; cannot choose the $label safely"
        }
        if (required) require(existing.isNotEmpty()) { "Missing $label" }
        return existing.singleOrNull()
    }
}
