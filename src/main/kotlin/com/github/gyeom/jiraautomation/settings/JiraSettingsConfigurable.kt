package com.github.gyeom.jiraautomation.settings

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.services.AIService
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
    private val aiService = project.service<AIService>()

    // Jira fields
    private val jiraUrlField = JBTextField()
    private val jiraUsernameField = JBTextField()
    private val jiraApiTokenField = JBPasswordField()

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

    init {
        // Setup listeners
        aiProviderComboBox.addActionListener { updateAIModels() }
    }

    private fun testJiraConnection() {
        // Save current Jira credentials temporarily
        val tempUrl = jiraUrlField.text
        val tempUsername = jiraUsernameField.text
        val tempToken = String(jiraApiTokenField.password)

        if (tempUrl.isEmpty() || tempUsername.isEmpty() || tempToken.isEmpty()) {
            com.intellij.openapi.ui.Messages.showWarningDialog(
                project,
                "Please fill in all Jira credentials before testing connection.",
                "Missing Credentials"
            )
            return
        }

        // Temporarily apply settings for API call
        val originalUrl = settings.state.jiraUrl
        val originalUsername = settings.state.jiraUsername
        val originalToken = settings.state.jiraApiToken

        settings.state.jiraUrl = tempUrl
        settings.state.jiraUsername = tempUsername
        settings.state.jiraApiToken = tempToken

        Thread {
            val result = jiraApiService.testConnection()

            // Restore original settings
            settings.state.jiraUrl = originalUrl
            settings.state.jiraUsername = originalUsername
            settings.state.jiraApiToken = originalToken

            SwingUtilities.invokeLater {
                result.onSuccess {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        project,
                        "Successfully connected to Jira!\n\nConnection is working properly.",
                        "Connection Test Successful"
                    )
                }.onFailure { error ->
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Failed to connect to Jira:\n\n${error.message}\n\nPlease check your credentials and URL.",
                        "Connection Test Failed"
                    )
                }
            }
        }.start()
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
                    button("Test Jira Connection") { testJiraConnection() }
                        .comment("Test your Jira credentials and connection")
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


    private fun updateAIModels() {
        val provider = aiProviderComboBox.selectedItem as? String ?: return

        // First, load fallback models immediately (synchronous)
        aiModelComboBox.removeAllItems()
        val fallbackModels = aiService.getFallbackModels(provider)
        fallbackModels.forEach { aiModelComboBox.addItem(it) }

        // Select the current model if it exists
        val currentModel = settings.state.aiModel
        if (fallbackModels.contains(currentModel)) {
            aiModelComboBox.selectedItem = currentModel
        } else if (fallbackModels.isNotEmpty()) {
            aiModelComboBox.selectedIndex = 0
        }

        // Then, try to fetch latest models from API in background (asynchronous)
        Thread {
            val apiKey = String(aiApiKeyField.password).takeIf { it.isNotEmpty() }
                ?: settings.state.aiApiKey

            if (apiKey.isEmpty()) {
                println("No API key - using fallback models")
                return@Thread
            }

            val result = when (provider.lowercase()) {
                "openai" -> aiService.fetchOpenAIModels(apiKey)
                "anthropic" -> aiService.fetchAnthropicModels(apiKey)
                else -> null
            }

            result?.onSuccess { apiModels ->
                SwingUtilities.invokeLater {
                    val currentSelection = aiModelComboBox.selectedItem as? String

                    aiModelComboBox.removeAllItems()
                    apiModels.forEach { aiModelComboBox.addItem(it) }

                    // Restore selection or select first
                    if (currentSelection != null && apiModels.contains(currentSelection)) {
                        aiModelComboBox.selectedItem = currentSelection
                    } else if (apiModels.isNotEmpty()) {
                        aiModelComboBox.selectedIndex = 0
                    }

                    println("Updated model list from API: ${apiModels.size} models")
                }
            }
        }.start()
    }

    override fun isModified(): Boolean {
        val state = settings.state
        val selectedLanguage = languageComboBox.selectedItem as? OutputLanguage

        return jiraUrlField.text != state.jiraUrl ||
                jiraUsernameField.text != state.jiraUsername ||
                String(jiraApiTokenField.password) != state.jiraApiToken ||
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
    }
}