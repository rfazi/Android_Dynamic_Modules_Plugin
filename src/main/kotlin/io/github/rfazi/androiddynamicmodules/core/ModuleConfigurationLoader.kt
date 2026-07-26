package io.github.rfazi.androiddynamicmodules.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object ModuleConfigurationLoader {
    const val FILE_NAME = ".android-dynamic-modules.properties"

    fun load(projectRoot: Path): ModuleConfiguration {
        val configurationPath = projectRoot.resolve(FILE_NAME)
        if (!Files.exists(configurationPath)) return ModuleConfiguration()

        val properties = Properties().apply {
            Files.newBufferedReader(configurationPath).use(::load)
        }
        val applicationModule = properties.getProperty("applicationModule")
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizeModulePath)
            ?: ":app"

        return ModuleConfiguration(
            applicationModule = applicationModule,
            applicationModuleDirectory = properties.getProperty("applicationModuleDirectory")
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            requiredModules = parseModulePaths(properties.getProperty("requiredModules"))
                .ifEmpty { setOf(applicationModule) },
            hiddenModules = parseModulePaths(properties.getProperty("hiddenModules")),
            dynamicFeaturePrefixes = parseList(properties.getProperty("dynamicFeaturePrefixes"))
                .ifEmpty { setOf("df_") },
        )
    }

    private fun parseModulePaths(value: String?): Set<String> =
        parseList(value).mapTo(linkedSetOf(), ::normalizeModulePath)

    private fun parseList(value: String?): Set<String> = value
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toCollection(linkedSetOf())
        .orEmpty()
}
