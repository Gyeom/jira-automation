package com.github.gyeom.jiraautomation.settings

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

class JiraSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = JiraSettingsState.getInstance(project)
    private val jiraApiService = project.service<JiraApiService>()

    // Jira fields
    private val jiraUrlField = JBTextField()
    private val jiraUsernameField = JBTextField()
    private val jiraApiTokenField = JBPasswordField()

    // Project and Issue Type - using editable ComboBoxes
    private val projectKeyComboBox = ComboBox<ProjectItem>()
    private val issueTypeComboBox = ComboBox<IssueTypeItem>()

    // AI Configuration - using ComboBoxes
    private val aiProviderComboBox = ComboBox(arrayOf("openai", "anthropic"))
    private val aiApiKeyField = JBPasswordField()
    private val aiModelComboBox = ComboBox<String>()

    // Language - using ComboBox
    private val languageComboBox = ComboBox(OutputLanguage.values())

    // Checkboxes
    private var rememberLastLanguage = false
    private var autoDetectLanguage = false
    private var includeDiffInDescription = true
    private var linkToCommit = true

    // Loading state
    private var isLoadingMetadata = false

    // Data classes
    data class ProjectItem(val key: String, val name: String) {
        override fun toString() = "$key - ${name}"
    }

    data class IssueTypeItem(val id: String, val name: String) {
        override fun toString() = name
    }

    init {
        // Make project key combo editable for manual entry
        projectKeyComboBox.isEditable = true

        // Setup listeners
        aiProviderComboBox.addActionListener { updateAIModels() }
        projectKeyComboBox.addActionListener { loadIssueTypesForProject() }

        // Add listeners for Jira credentials to enable Load button
        jiraUrlField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
        })
        jiraUsernameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
        })
        jiraApiTokenField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = checkCredentials()
        })
    }

    private fun checkCredentials() {
        // This method can be used to enable/disable the Load Projects button
    }

    override fun getDisplayName(): String = "Jira Ticket Creator"

    override fun createComponent(): JComponent {
        return panel {
            group("Jira Configuration") {
                row("Jira URL:") {
                    cell(jiraUrlField)
                        .columns(30)
                        .comment("Example: https://your-company.atlassian.net")
                }
                row("Username/Email:") {
                    cell(jiraUsernameField)
                        .columns(30)
                }
                row("API Token:") {
                    cell(jiraApiTokenField)
                        .columns(30)
                        .comment("Generate from Jira Settings → Security → API tokens")
                }

                separator()

                row {
                    button("Load Projects from Jira") { loadJiraMetadata() }
                        .comment("Click to load available projects and issue types from your Jira instance")
                }

                row("Default Project:") {
                    cell(projectKeyComboBox)
                        .columns(20)
                        .comment("Select or type a project key. Load projects first for dropdown options.")
                }
                row("Default Issue Type:") {
                    cell(issueTypeComboBox)
                        .columns(20)
                        .comment("Select issue type. Will update based on selected project.")
                }
            }

            group("AI Configuration") {
                row("AI Provider:") {
                    cell(aiProviderComboBox)
                        .columns(15)
                        .comment("Select AI provider")
                }
                row("API Key:") {
                    cell(aiApiKeyField)
                        .columns(30)
                        .comment("Enter your AI provider API key")
                }
                row("Model:") {
                    cell(aiModelComboBox)
                        .columns(25)
                        .comment("Select AI model")
                }
            }

            group("Language Settings") {
                row("Default Language:") {
                    cell(languageComboBox)
                        .columns(15)
                        .comment("Select default output language")
                }
                row {
                    checkBox("Remember last used language")
                        .bindSelected(::rememberLastLanguage)
                }
                row {
                    checkBox("Auto-detect from IDE language")
                        .bindSelected(::autoDetectLanguage)
                }
            }

            group("Template Settings") {
                row {
                    checkBox("Include diff details in description")
                        .bindSelected(::includeDiffInDescription)
                }
                row {
                    checkBox("Link to commit/branch")
                        .bindSelected(::linkToCommit)
                }
            }
        }
    }

    private fun loadJiraMetadata() {
        if (isLoadingMetadata) return

        // Save current Jira credentials temporarily
        val tempState = settings.state.copy()
        tempState.jiraUrl = jiraUrlField.text
        tempState.jiraUsername = jiraUsernameField.text
        tempState.jiraApiToken = String(jiraApiTokenField.password)

        // Temporarily apply settings for API calls
        settings.state.jiraUrl = tempState.jiraUrl
        settings.state.jiraUsername = tempState.jiraUsername
        settings.state.jiraApiToken = tempState.jiraApiToken

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

                        // Select current project if exists
                        val currentKey = settings.state.defaultProjectKey
                        val currentProject = projectItems.find { it.key == currentKey }
                        if (currentProject != null) {
                            projectKeyComboBox.selectedItem = currentProject
                        } else if (projectItems.isNotEmpty()) {
                            projectKeyComboBox.selectedIndex = 0
                        }

                        // Load issue types for selected project
                        loadIssueTypesForProject()
                    }
                }.onFailure { error ->
                    SwingUtilities.invokeLater {
                        // Fall back to manual entry
                        projectKeyComboBox.removeAllItems()
                        projectKeyComboBox.addItem(
                            ProjectItem(settings.state.defaultProjectKey, "Manual Entry")
                        )
                        println("Failed to load projects: ${error.message}")
                    }
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
            val issueTypesResult = jiraApiService.getIssueTypesForProject(selectedProject.key)
            issueTypesResult.onSuccess { issueTypes ->
                SwingUtilities.invokeLater {
                    issueTypeComboBox.removeAllItems()

                    // Filter out subtask types and add to combo box
                    issueTypes.filter { !it.subtask }.forEach { issueType ->
                        issueTypeComboBox.addItem(IssueTypeItem(issueType.id, issueType.name))
                    }

                    // Select current issue type if exists
                    val currentType = settings.state.defaultIssueType
                    for (i in 0 until issueTypeComboBox.itemCount) {
                        val item = issueTypeComboBox.getItemAt(i)
                        if (item.name == currentType) {
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

        // Select the current model if it exists
        val currentModel = settings.state.aiModel
        if (models.contains(currentModel)) {
            aiModelComboBox.selectedItem = currentModel
        }
    }

    override fun isModified(): Boolean {
        val state = settings.state
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
        val selectedIssueType = issueTypeComboBox.selectedItem as? IssueTypeItem
        val selectedLanguage = languageComboBox.selectedItem as? OutputLanguage

        // Handle editable combo box text input
        val projectKey = if (selectedProject != null) {
            selectedProject.key
        } else {
            projectKeyComboBox.editor?.item?.toString() ?: ""
        }

        return jiraUrlField.text != state.jiraUrl ||
                jiraUsernameField.text != state.jiraUsername ||
                String(jiraApiTokenField.password) != state.jiraApiToken ||
                projectKey != state.defaultProjectKey ||
                selectedIssueType?.name != state.defaultIssueType ||
                aiProviderComboBox.selectedItem != state.aiProvider ||
                String(aiApiKeyField.password) != state.aiApiKey ||
                aiModelComboBox.selectedItem != state.aiModel ||
                selectedLanguage?.code != state.defaultLanguage ||
                rememberLastLanguage != state.rememberLastLanguage ||
                autoDetectLanguage != state.autoDetectLanguage ||
                includeDiffInDescription != state.includeDiffInDescription ||
                linkToCommit != state.linkToCommit
    }

    override fun apply() {
        val state = settings.state
        state.jiraUrl = jiraUrlField.text
        state.jiraUsername = jiraUsernameField.text
        state.jiraApiToken = String(jiraApiTokenField.password)

        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
        if (selectedProject != null) {
            state.defaultProjectKey = selectedProject.key
        } else {
            // Handle manual text entry
            val manualKey = projectKeyComboBox.editor?.item?.toString()
            if (!manualKey.isNullOrBlank()) {
                state.defaultProjectKey = manualKey
            }
        }

        val selectedIssueType = issueTypeComboBox.selectedItem as? IssueTypeItem
        if (selectedIssueType != null) {
            state.defaultIssueType = selectedIssueType.name
        }

        state.aiProvider = aiProviderComboBox.selectedItem as? String ?: "anthropic"
        state.aiApiKey = String(aiApiKeyField.password)
        state.aiModel = aiModelComboBox.selectedItem as? String ?: ""

        val selectedLanguage = languageComboBox.selectedItem as? OutputLanguage
        if (selectedLanguage != null) {
            state.defaultLanguage = selectedLanguage.code
        }

        state.rememberLastLanguage = rememberLastLanguage
        state.autoDetectLanguage = autoDetectLanguage
        state.includeDiffInDescription = includeDiffInDescription
        state.linkToCommit = linkToCommit
    }

    override fun reset() {
        val state = settings.state
        jiraUrlField.text = state.jiraUrl
        jiraUsernameField.text = state.jiraUsername
        jiraApiTokenField.text = state.jiraApiToken

        // Reset AI provider and models
        aiProviderComboBox.selectedItem = state.aiProvider
        updateAIModels()
        aiApiKeyField.text = state.aiApiKey
        aiModelComboBox.selectedItem = state.aiModel

        // Reset language
        val language = OutputLanguage.fromCode(state.defaultLanguage)
        languageComboBox.selectedItem = language

        rememberLastLanguage = state.rememberLastLanguage
        autoDetectLanguage = state.autoDetectLanguage
        includeDiffInDescription = state.includeDiffInDescription
        linkToCommit = state.linkToCommit

        // If we have saved project/issue type, set them as manual entries
        projectKeyComboBox.removeAllItems()
        projectKeyComboBox.addItem(
            ProjectItem(state.defaultProjectKey, "Saved Project")
        )
        projectKeyComboBox.selectedIndex = 0

        issueTypeComboBox.removeAllItems()
        issueTypeComboBox.addItem(IssueTypeItem(state.defaultIssueType, state.defaultIssueType))
        issueTypeComboBox.selectedIndex = 0
    }
}