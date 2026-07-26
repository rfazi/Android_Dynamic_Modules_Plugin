package io.github.rfazi.androiddynamicmodules

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.rfazi.androiddynamicmodules.core.ModuleChangePlan

object ModuleDocumentApplier {
    fun apply(project: Project, plan: ModuleChangePlan) {
        if (plan.changes.isEmpty()) return

        val fileDocumentManager = FileDocumentManager.getInstance()
        val documents = plan.changes.map { change ->
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(change.path)
                ?: error("Cannot find ${change.path} in the IDE file system")
            val document = fileDocumentManager.getDocument(virtualFile)
                ?: error("Cannot open ${change.path.fileName} as a text document")
            require(document.text == change.original) {
                "${change.path.fileName} changed after the module list was loaded. Reopen the dialog and try again."
            }
            Triple(change, virtualFile, document)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                documents.forEach { (change, _, document) -> document.setText(change.updated) }
            } catch (error: Throwable) {
                documents.forEach { (change, _, document) ->
                    if (document.text != change.original) document.setText(change.original)
                }
                throw error
            }
        }

        documents.forEach { (_, _, document) -> fileDocumentManager.saveDocument(document) }
    }
}
