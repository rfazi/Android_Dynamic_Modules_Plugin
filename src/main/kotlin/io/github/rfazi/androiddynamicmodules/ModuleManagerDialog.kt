package io.github.rfazi.androiddynamicmodules

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.rfazi.androiddynamicmodules.core.ProjectModulesSnapshot
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ModuleManagerDialog(
    project: Project,
    snapshot: ProjectModulesSnapshot,
) : DialogWrapper(project, true) {
    private val panel = ModuleManagerPanel(snapshot)

    init {
        title = "Manage Android Modules"
        setOKButtonText("Apply and Sync")
        init()
    }

    override fun createCenterPanel(): JComponent = panel

    fun selectedStates(): Map<String, Boolean> = panel.selectedStates()
}

private class ModuleManagerPanel(snapshot: ProjectModulesSnapshot) : JPanel(BorderLayout()) {
    private val configuration = snapshot.configuration
    private val selectedStates = snapshot.modules.associate { it.path to it.enabled }.toMutableMap()
    private val checkBoxes = linkedMapOf<String, JBCheckBox>()
    private val modulesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }

    init {
        border = JBUI.Borders.empty(8)

        val filterField = JBTextField().apply {
            emptyText.text = "Filter modules"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = updateFilter(text)
                override fun removeUpdate(event: DocumentEvent) = updateFilter(text)
                override fun changedUpdate(event: DocumentEvent) = updateFilter(text)
            })
        }

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel("Project: ${snapshot.projectRoot}"))
            add(JBLabel("Changes are previewed in memory and applied as one IDE command."))
            add(filterField)
            add(JPanel(FlowLayout(FlowLayout.LEADING, 0, 4)).apply {
                add(JButton("Enable all").apply { addActionListener { selectAll() } })
                add(JButton("Light preset").apply { addActionListener { selectLightPreset() } })
            })
        }
        add(header, BorderLayout.NORTH)

        snapshot.modules
            .filterNot { it.path in configuration.hiddenModules }
            .sortedBy { it.path }
            .forEach { module ->
                val required = module.path in configuration.requiredModules
                val checkBox = JBCheckBox(module.path, module.enabled || required).apply {
                    isEnabled = !required
                    toolTipText = if (required) "Required module" else null
                    addActionListener { selectedStates[module.path] = isSelected }
                }
                selectedStates[module.path] = checkBox.isSelected
                checkBoxes[module.path] = checkBox
                modulesPanel.add(checkBox)
            }

        add(JBScrollPane(modulesPanel).apply {
            preferredSize = Dimension(560, 420)
            border = JBUI.Borders.emptyTop(6)
        }, BorderLayout.CENTER)
    }

    fun selectedStates(): Map<String, Boolean> = selectedStates.toMap()

    private fun selectAll() {
        checkBoxes.forEach { (path, checkBox) ->
            checkBox.isSelected = true
            selectedStates[path] = true
        }
    }

    private fun selectLightPreset() {
        checkBoxes.forEach { (path, checkBox) ->
            val selected = path in configuration.requiredModules
            checkBox.isSelected = selected
            selectedStates[path] = selected
        }
    }

    private fun updateFilter(query: String) {
        val normalizedQuery = query.trim().lowercase()
        checkBoxes.forEach { (path, checkBox) ->
            checkBox.isVisible = normalizedQuery.isEmpty() || normalizedQuery in path.lowercase()
        }
        modulesPanel.revalidate()
        modulesPanel.repaint()
    }
}
