package com.github.gyeom.jiraautomation.actions

import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.ui.CreateJiraTicketDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class CreateJiraFromDiffAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val diffAnalysisService = project.service<DiffAnalysisService>()

        // Analyze uncommitted changes
        val diffResult = diffAnalysisService.analyzeUncommittedChanges()

        if (diffResult == null) {
            Messages.showInfoMessage(
                project,
                "No uncommitted changes found. Please make some changes before creating a Jira ticket.",
                "No Changes"
            )
            return
        }

        // Show dialog
        val dialog = CreateJiraTicketDialog(project, diffResult)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}
