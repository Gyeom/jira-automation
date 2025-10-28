package com.github.gyeom.jiraautomation.ui

import com.github.gyeom.jiraautomation.model.OutputLanguage
import com.github.gyeom.jiraautomation.services.AIService
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class CreateJiraTicketDialog(
    private val project: Project,
    private val diffResult: DiffAnalysisService.DiffAnalysisResult
) : DialogWrapper(project) {

    private val settings = JiraSettingsState.getInstance(project)
    private val aiService = project.service<AIService>()
    private val jiraApiService = project.service<JiraApiService>()
    private val diffAnalysisService = project.service<DiffAnalysisService>()

    private val languageComboBox = ComboBox(OutputLanguage.values())
    private val titleField = JBTextField(60)
    private val descriptionArea = JBTextArea(15, 60)
    private val projectKeyField = JBTextField(settings.state.defaultProjectKey, 20)
    private val issueTypeField = JBTextField(settings.state.defaultIssueType, 20)
    private val priorityComboBox = ComboBox(arrayOf(
        "",
        "P0 - ASAP",
        "P1 - High",
        "P2 - Medium",
        "P3 - Low",
        "P5 - Need to consider",
        "Highest",
        "High",
        "Medium",
        "Low",
        "Lowest"
    ))

    private var isGenerating = false

    init {
        title = "Create Jira Ticket from Code Changes"
        init()

        // Set default language
        val defaultLang = OutputLanguage.fromCode(settings.state.defaultLanguage)
        languageComboBox.selectedItem = defaultLang

        // Auto-generate on init
        SwingUtilities.invokeLater {
            generateTicket()
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)

        var row = 0

        // Language selection
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Output Language:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        val languagePanel = JPanel(BorderLayout(5, 0))
        languagePanel.add(languageComboBox, BorderLayout.WEST)

        val regenerateButton = JButton("Regenerate")
        regenerateButton.addActionListener { generateTicket() }
        languagePanel.add(regenerateButton, BorderLayout.EAST)

        panel.add(languagePanel, gbc)
        row++

        // Title
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Title:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(titleField, gbc)
        row++

        // Description
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Description:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        descriptionArea.lineWrap = true
        descriptionArea.wrapStyleWord = true
        val scrollPane = JBScrollPane(descriptionArea)
        scrollPane.preferredSize = Dimension(600, 300)
        panel.add(scrollPane, gbc)
        row++

        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST

        // Separator
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        panel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        row++

        // Project Key
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Project Key:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(projectKeyField, gbc)
        row++

        // Issue Type
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Issue Type:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(issueTypeField, gbc)
        row++

        // Priority
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Priority:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(priorityComboBox, gbc)
        row++

        return panel
    }

    private fun generateTicket() {
        if (isGenerating) return

        isGenerating = true
        titleField.text = "Generating..."
        descriptionArea.text = "Please wait while AI generates the ticket content..."
        titleField.isEnabled = false
        descriptionArea.isEnabled = false

        Thread {
            try {
                val language = languageComboBox.selectedItem as OutputLanguage

                val diffSummary = diffAnalysisService.formatDiffSummary(diffResult)
                val result = aiService.generateTicketFromDiff(
                    diffSummary,
                    diffResult.diffContent,
                    language
                )

                SwingUtilities.invokeLater {
                    result.onSuccess { ticket ->
                        titleField.text = ticket.title
                        descriptionArea.text = ticket.description
                    }.onFailure { error ->
                        titleField.text = "[AI Error] Code Changes"
                        descriptionArea.text = "Failed to generate with AI: ${error.message}\n\n" +
                                "Please edit manually.\n\n$diffSummary"
                        Messages.showErrorDialog(
                            project,
                            "Failed to generate ticket: ${error.message}",
                            "AI Generation Error"
                        )
                    }

                    titleField.isEnabled = true
                    descriptionArea.isEnabled = true
                    isGenerating = false
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    titleField.text = "Error generating ticket"
                    descriptionArea.text = e.message ?: "Unknown error"
                    titleField.isEnabled = true
                    descriptionArea.isEnabled = true
                    isGenerating = false
                }
            }
        }.start()
    }

    override fun doOKAction() {
        val title = titleField.text.trim()
        val description = descriptionArea.text.trim()
        val projectKey = projectKeyField.text.trim()
        val issueType = issueTypeField.text.trim()
        val priority = priorityComboBox.selectedItem as? String

        if (title.isEmpty()) {
            Messages.showErrorDialog(project, "Title is required", "Validation Error")
            return
        }

        if (projectKey.isEmpty()) {
            Messages.showErrorDialog(project, "Project Key is required", "Validation Error")
            return
        }

        // Create Jira issue
        val result = jiraApiService.createIssue(
            title = title,
            description = description,
            projectKey = projectKey,
            issueType = issueType,
            priority = if (priority.isNullOrEmpty()) null else priority
        )

        result.onSuccess { issue ->
            val jiraUrl = "${settings.state.jiraUrl}/browse/${issue.key}"
            Messages.showInfoMessage(
                project,
                "Jira ticket created successfully!\n\nKey: ${issue.key}\nURL: $jiraUrl",
                "Success"
            )
            super.doOKAction()
        }.onFailure { error ->
            Messages.showErrorDialog(
                project,
                "Failed to create Jira ticket:\n${error.message}",
                "Error"
            )
        }
    }
}
