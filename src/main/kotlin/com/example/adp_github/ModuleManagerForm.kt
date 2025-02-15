package com.example.adp_github

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.*


class ModuleManagerForm(private val project: Project) {
    private val mainPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel("Remember: DON'T push edited gradle files!!!"))
        add(JLabel("Project: ${project.basePath}"))
        add(JButton("Default Preset").apply {
            addActionListener {
                modulesCheckBox.forEach {
                    it.value.isSelected = true
                }
            }
        })
        add(JButton("Light Preset").apply {
            addActionListener {
                modulesCheckBox.forEach {
                    it.value.isSelected = disabled.contains(it.key)
                }
            }
        })
    }
    private val modulesCheckBox: MutableMap<String, JCheckBox> = mutableMapOf()
    private val disabled = listOf(
        "app"
    )
    private val hiddenModules = listOf(
        "hiddenmodule"
    )

    init {
        loadModuleStates()
        modulesCheckBox.toSortedMap().forEach { mainPanel.add(it.value) }
    }

    private fun loadModuleStates() {
        kotlin.runCatching {
            val lines: List<String> = kotlin.runCatching {
                Files.readAllLines(Paths.get(project.basePath, "settings.gradle.kts"))
            }.getOrElse {
                Files.readAllLines(Paths.get(project.basePath, "settings.gradle"))
            }

            lines.forEach { line ->
                if (line.contains("include")) {
                    val module = Regex("include\\s?[\"'(]{1,2}(?<name>[^\"']+)[\"')]{1,2}").find(line)
                    module?.let {
                        kotlin.runCatching {
                            it.groups["name"]?.let { name ->
                                modulesCheckBox[name.value] = JCheckBox(name.value).apply {
                                    isSelected = line.contains("//").not()
                                    isEnabled = disabled.contains(name.value).not()
                                    isVisible = hiddenModules.contains(name.value).not()
                                    addActionListener {
                                        modulesCheckBox[name.value]!!.isSelected = isSelected
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    private fun applyChanges() {
        kotlin.runCatching {
            val oldSettingsFile = Paths.get(project.basePath, "settings.gradle").toFile()
            val ktsSettingsFile = Paths.get(project.basePath, "settings.gradle.kts").toFile()
            val appGradleFile = Paths.get(project.basePath, "app", "build.gradle").toFile()

            val settingsFile = if (oldSettingsFile.exists()) oldSettingsFile else ktsSettingsFile

            val settingLines: List<String> = kotlin.runCatching {
                Files.readAllLines(settingsFile.toPath())
            }.getOrElse {
                it.printStackTrace()
                emptyList<String>()
            }

            val newSettingsLines = settingLines.map { line: String ->
                if (line.contains("include")) {
                    modulesCheckBox.forEach {
                        if (line.contains(it.key)) {
                            val newLine = line.replace("//", "")
                            return@map if (it.value.isSelected) newLine else "//$newLine"
                        }
                    }
                }
                line
            }

            Files.write(settingsFile.toPath(), newSettingsLines)

            val appGradleLines: List<String> = kotlin.runCatching {
                Files.readAllLines(appGradleFile.toPath())
            }.getOrElse {
                it.printStackTrace()
                emptyList<String>()
            }

            val newAppGradleLines = appGradleLines.map { line: String ->
                if (line.contains("dynamicFeatures")) {
                    val df = modulesCheckBox.filter { it.key.contains("df_") && it.value.isSelected }
                            .map {
                                "'${it.key}'"
                            }
                    return@map "\tdynamicFeatures = [${df.joinToString(", ")}]"
                }
                line
            }

            Files.write(appGradleFile.toPath(), newAppGradleLines)
        }.onFailure { it.printStackTrace() }
    }

    fun getMainPanel(): JPanel {
        return mainPanel
    }

    fun getScrollPane(): JScrollPane {
        return JBScrollPane(mainPanel)
    }

    fun saveChanges() {
        applyChanges()
    }

    private fun putLog(log: String) {
        mainPanel.add(JLabel(log))
    }
}