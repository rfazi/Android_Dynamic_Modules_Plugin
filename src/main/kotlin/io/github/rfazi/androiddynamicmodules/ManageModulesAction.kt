package io.github.rfazi.androiddynamicmodules

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.github.rfazi.androiddynamicmodules.core.ModuleProjectService
import io.github.rfazi.androiddynamicmodules.core.ProjectModulesSnapshot
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Files
import java.nio.file.Path

class ManageModulesAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val root = event.project?.basePath?.let(Path::of)
        event.presentation.isEnabledAndVisible = root != null && (
            Files.isRegularFile(root.resolve("settings.gradle.kts")) ||
                Files.isRegularFile(root.resolve("settings.gradle"))
            )
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectRoot = project.basePath?.let(Path::of)
            ?: return showError(project, "The project has no local base directory")

        ProgressManager.getInstance().run(object : Task.Modal(project, "Loading Gradle modules", false) {
            private lateinit var snapshot: ProjectModulesSnapshot

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                snapshot = ModuleProjectService.load(projectRoot)
            }

            override fun onSuccess() {
                showModuleDialog(project, snapshot)
            }

            override fun onThrowable(error: Throwable) {
                showError(project, error.userMessage())
            }
        })
    }

    private fun showModuleDialog(project: Project, snapshot: ProjectModulesSnapshot) {
        val dialog = ModuleManagerDialog(project, snapshot)
        if (!dialog.showAndGet()) return

        runCatching {
            ModuleProjectService.plan(snapshot, dialog.selectedStates())
        }.onSuccess { plan ->
            if (plan.changes.isEmpty()) {
                Messages.showInfoMessage(project, "No module changes to apply.", "Android Dynamic Modules")
                return@onSuccess
            }

            runCatching {
                ModuleDocumentApplier.apply(project, plan)
                syncGradleProject(project)
            }.onSuccess {
                val changedFiles = plan.changes.joinToString("\n") { "• ${it.path.fileName}" }
                val warnings = plan.warnings.takeIf(List<String>::isNotEmpty)
                    ?.joinToString(separator = "\n", prefix = "\n\nWarnings:\n")
                    .orEmpty()
                Messages.showInfoMessage(
                    project,
                    "Updated files:\n$changedFiles$warnings\n\nGradle sync has started.",
                    "Android Dynamic Modules",
                )
            }.onFailure { showError(project, it.userMessage()) }
        }.onFailure { showError(project, it.userMessage()) }
    }

    private fun syncGradleProject(project: Project) {
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }

    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, "Android Dynamic Modules")
    }

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
        ?: this::class.simpleName
        ?: "Unexpected error"
}
