package com.github.gyeom.jiraautomation.ui

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.services.AIService
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionComboBoxModel
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

    // ComboBoxes with search functionality
    private val projectKeyComboBox = ComboBox<ProjectItem>()
    private val issueTypeComboBox = ComboBox<IssueTypeItem>()
    private val priorityComboBox = ComboBox<PriorityItem>()

    // AI Provider selection
    private val aiProviderComboBox = ComboBox(arrayOf("openai", "anthropic"))
    private val aiModelComboBox = ComboBox<String>()

    private var isGenerating = false
    private var isLoadingMetadata = false

    // Data classes for ComboBox items
    data class ProjectItem(val key: String, val name: String) {
        override fun toString() = "$key - $name"
    }

    data class IssueTypeItem(val id: String, val name: String) {
        override fun toString() = name
    }

    data class PriorityItem(val id: String, val name: String) {
        override fun toString() = name
    }

    init {
        title = "Create Jira Ticket from Code Changes"
        init()

        // Make project combo editable for filtering and manual entry
        projectKeyComboBox.isEditable = true
        setupProjectComboBoxFilter()

        // Set default language
        val defaultLang = OutputLanguage.fromCode(settings.state.defaultLanguage)
        languageComboBox.selectedItem = defaultLang

        // Set default AI provider and model
        aiProviderComboBox.selectedItem = settings.state.aiProvider
        updateAIModels()
        aiModelComboBox.selectedItem = settings.state.aiModel

        // Setup listeners
        projectKeyComboBox.addActionListener { loadIssueTypesForProject() }
        aiProviderComboBox.addActionListener { updateAIModels() }

        // Load Jira metadata
        SwingUtilities.invokeLater {
            loadJiraMetadata()
            generateTicket()
        }
    }

    private fun setupProjectComboBoxFilter() {
        // Add a simple filter functionality using the editor
        val editor = projectKeyComboBox.editor
        if (editor?.editorComponent is JTextField) {
            val textField = editor.editorComponent as JTextField
            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()

                private fun filterProjects() {
                    val text = textField.text.lowercase()
                    if (text.isEmpty()) return

                    // Simple filtering - shows popup when typing
                    if (!projectKeyComboBox.isPopupVisible && projectKeyComboBox.itemCount > 0) {
                        projectKeyComboBox.showPopup()
                    }
                }
            })
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

        // AI Provider selection
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("AI Provider:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        val aiPanel = JPanel(BorderLayout(5, 0))
        aiPanel.add(aiProviderComboBox, BorderLayout.WEST)
        aiPanel.add(JBLabel(" Model: "), BorderLayout.CENTER)
        aiPanel.add(aiModelComboBox, BorderLayout.EAST)
        panel.add(aiPanel, gbc)
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
        panel.add(projectKeyComboBox, gbc)
        row++

        // Issue Type
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("Issue Type:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(issueTypeComboBox, gbc)
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

    private fun loadJiraMetadata() {
        if (isLoadingMetadata) return
        isLoadingMetadata = true

        Thread {
            try {
                // Load projects
                val projectsResult = jiraApiService.getProjects()
                projectsResult.onSuccess { projects ->
                    SwingUtilities.invokeLater {
                        projectKeyComboBox.removeAllItems()
                        val projectItems = projects.map { project ->
                            ProjectItem(project.key, project.name)
                        }
                        projectItems.forEach { projectKeyComboBox.addItem(it) }

                        // Select default project if exists
                        val defaultKey = settings.state.defaultProjectKey
                        val defaultProject = projectItems.find { it.key == defaultKey }
                        if (defaultProject != null) {
                            projectKeyComboBox.selectedItem = defaultProject
                        }

                        // Load issue types for selected project
                        loadIssueTypesForProject()
                    }
                }.onFailure { error ->
                    SwingUtilities.invokeLater {
                        // Fall back to manual entry with default value
                        projectKeyComboBox.removeAllItems()
                        projectKeyComboBox.addItem(
                            ProjectItem(settings.state.defaultProjectKey, "Manual Entry")
                        )
                        println("Failed to load projects: ${error.message}")
                    }
                }

                // Don't load global priorities here - they will be loaded per project
                // Just set initial state
                SwingUtilities.invokeLater {
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    println("Priorities will be loaded when project is selected")
                }

            } catch (e: Exception) {
                println("Error loading Jira metadata: ${e.message}")
            } finally {
                isLoadingMetadata = false
            }
        }.start()
    }

    private fun loadIssueTypesForProject() {
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        Thread {
            // Load issue types
            val issueTypesResult = jiraApiService.getIssueTypesForProject(selectedProject.key)
            issueTypesResult.onSuccess { issueTypes ->
                SwingUtilities.invokeLater {
                    issueTypeComboBox.removeAllItems()

                    // Filter out subtask types and add to combo box
                    issueTypes.filter { !it.subtask }.forEach { issueType ->
                        issueTypeComboBox.addItem(IssueTypeItem(issueType.id, issueType.name))
                    }

                    // Select default issue type if exists
                    val defaultType = settings.state.defaultIssueType
                    for (i in 0 until issueTypeComboBox.itemCount) {
                        val item = issueTypeComboBox.getItemAt(i)
                        if (item.name == defaultType) {
                            issueTypeComboBox.selectedIndex = i
                            break
                        }
                    }
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    // Fall back to common issue types
                    issueTypeComboBox.removeAllItems()
                    arrayOf("Task", "Story", "Bug", "Epic").forEach {
                        issueTypeComboBox.addItem(IssueTypeItem(it, it))
                    }
                    println("Failed to load issue types: ${error.message}")
                }
            }

            // Load project-specific priorities
            val prioritiesResult = jiraApiService.getProjectPriorities(selectedProject.key)
            prioritiesResult.onSuccess { priorities ->
                SwingUtilities.invokeLater {
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    priorities.forEach { priority ->
                        priorityComboBox.addItem(PriorityItem(priority.id, priority.name))
                    }
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    // Fall back to no priority selection
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    println("Failed to load project priorities: ${error.message}")
                }
            }
        }.start()
    }

    private fun updateAIModels() {
        val provider = aiProviderComboBox.selectedItem as? String ?: return

        aiModelComboBox.removeAllItems()

        val models = when (provider) {
            "openai" -> listOf(
                "gpt-4-turbo-preview",
                "gpt-4-turbo",
                "gpt-4",
                "gpt-3.5-turbo"
            )
            "anthropic" -> listOf(
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307"
            )
            else -> emptyList()
        }

        models.forEach { aiModelComboBox.addItem(it) }

        // Select the default model for this provider
        if (provider == settings.state.aiProvider && models.contains(settings.state.aiModel)) {
            aiModelComboBox.selectedItem = settings.state.aiModel
        }
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
                val provider = aiProviderComboBox.selectedItem as? String ?: settings.state.aiProvider
                val model = aiModelComboBox.selectedItem as? String ?: settings.state.aiModel

                // Temporarily override AI settings for this generation
                val originalProvider = settings.state.aiProvider
                val originalModel = settings.state.aiModel

                settings.state.aiProvider = provider
                settings.state.aiModel = model

                val diffSummary = diffAnalysisService.formatDiffSummary(diffResult)
                val result = aiService.generateTicketFromDiff(
                    diffSummary,
                    diffResult.diffContent,
                    language
                )

                // Restore original settings
                settings.state.aiProvider = originalProvider
                settings.state.aiModel = originalModel

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

        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
        val projectKey = selectedProject?.key
            ?: projectKeyComboBox.editor?.item?.toString()?.trim()
            ?: ""

        val issueType = (issueTypeComboBox.selectedItem as? IssueTypeItem)?.name
            ?: issueTypeComboBox.editor?.item?.toString()?.trim()
            ?: "Task"

        // Use priority ID instead of name for Jira API
        val selectedPriority = priorityComboBox.selectedItem as? PriorityItem
        val priority = if (selectedPriority != null && selectedPriority.id.isNotEmpty()) {
            println("Selected priority: id=${selectedPriority.id}, name=${selectedPriority.name}")
            selectedPriority.id  // Use ID for API call
        } else {
            println("No priority selected")
            null
        }

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
            priority = priority
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