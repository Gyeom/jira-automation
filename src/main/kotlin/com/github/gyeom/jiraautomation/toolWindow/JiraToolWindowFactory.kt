package com.github.gyeom.jiraautomation.toolWindow

import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.ui.CreateJiraTicketDialog
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton

class JiraToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = JiraToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(toolWindowContent.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class JiraToolWindowContent(private val project: Project) {

        private val diffAnalysisService = project.service<DiffAnalysisService>()
        private val jiraApiService = project.service<JiraApiService>()

        fun getContent(): JBPanel<*> {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
            mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // Title
            val titleLabel = JBLabel("<html><h2>Jira Ticket Creator</h2></html>")
            mainPanel.add(titleLabel, BorderLayout.NORTH)

            // Content panel
            val contentPanel = JBPanel<JBPanel<*>>()
            contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
            contentPanel.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)

            // Description
            val descriptionLabel = JBLabel(
                "<html>" +
                        "<p>Create Jira tickets automatically from your code changes.</p>" +
                        "<br>" +
                        "<p><b>How to use:</b></p>" +
                        "<ul>" +
                        "<li>Make code changes in your project</li>" +
                        "<li>Click 'Create from Changes' button</li>" +
                        "<li>AI will analyze your changes and generate a ticket</li>" +
                        "<li>Review and create the Jira issue</li>" +
                        "</ul>" +
                        "</html>"
            )
            contentPanel.add(descriptionLabel)

            contentPanel.add(Box.createVerticalStrut(20))

            // Buttons panel
            val buttonsPanel = JBPanel<JBPanel<*>>(GridLayout(3, 1, 0, 10))

            // Create from Changes button
            val createFromChangesButton = JButton("Create Ticket from Uncommitted Changes")
            createFromChangesButton.addActionListener {
                createTicketFromChanges()
            }
            buttonsPanel.add(createFromChangesButton)

            // Test Connection button
            val testConnectionButton = JButton("Test Jira Connection")
            testConnectionButton.addActionListener {
                testJiraConnection()
            }
            buttonsPanel.add(testConnectionButton)

            // Settings button
            val settingsButton = JButton("Open Settings")
            settingsButton.addActionListener {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "Jira Ticket Creator")
            }
            buttonsPanel.add(settingsButton)

            contentPanel.add(buttonsPanel)

            mainPanel.add(contentPanel, BorderLayout.CENTER)

            return mainPanel
        }

        private fun createTicketFromChanges() {
            val diffResult = diffAnalysisService.analyzeUncommittedChanges()

            if (diffResult == null) {
                Messages.showInfoMessage(
                    project,
                    "No uncommitted changes found.\nPlease make some changes before creating a Jira ticket.",
                    "No Changes"
                )
                return
            }

            val dialog = CreateJiraTicketDialog(project, diffResult)
            dialog.show()
        }

        private fun testJiraConnection() {
            val result = jiraApiService.testConnection()

            result.onSuccess {
                Messages.showInfoMessage(
                    project,
                    "Successfully connected to Jira!",
                    "Connection Test"
                )
            }.onFailure { error ->
                Messages.showErrorDialog(
                    project,
                    "Failed to connect to Jira:\n${error.message}\n\nPlease check your settings.",
                    "Connection Test Failed"
                )
            }
        }
    }
}
