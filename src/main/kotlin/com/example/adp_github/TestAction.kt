package com.example.adp_github

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent


class TestAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = ModuleManagerDialog(project)
        if (dialog.showAndGet()) {
            // Handle OK button press
            dialog.saveChanges()
            syncGradleProject(project)
        }
    }

    private fun syncGradleProject(project: Project) {
        ExternalSystemUtil.refreshProjects(
            ImportSpecBuilder(project, ProjectSystemId("GRADLE"))
                .createDirectoriesForEmptyContentRoots()
        )
    }

    private class ModuleManagerDialog(project: Project) : DialogWrapper(true) {
        private val form = ModuleManagerForm(project)

        init {
            init()
            title = "Modules Manager"
        }

        override fun createCenterPanel(): JComponent {
            return form.getScrollPane()
        }

        fun saveChanges() {
            form.saveChanges()
        }
    }

}