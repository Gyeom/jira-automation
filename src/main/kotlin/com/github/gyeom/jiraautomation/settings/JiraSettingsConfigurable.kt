package com.github.gyeom.jiraautomation.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class JiraSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = JiraSettingsState.getInstance(project)

    private val jiraUrlField = JBTextField()
    private val jiraUsernameField = JBTextField()
    private val jiraApiTokenField = JBPasswordField()
    private val defaultProjectKeyField = JBTextField()
    private val defaultIssueTypeField = JBTextField()
    private val aiProviderField = JBTextField()
    private val aiApiKeyField = JBPasswordField()
    private val aiModelField = JBTextField()
    private val defaultLanguageField = JBTextField()
    private var rememberLastLanguage = false
    private var autoDetectLanguage = false
    private var includeDiffInDescription = true
    private var linkToCommit = true

    override fun getDisplayName(): String = "Jira Ticket Creator"

    override fun createComponent(): JComponent {
        return panel {
            group("Jira Configuration") {
                row("Jira URL:") {
                    cell(jiraUrlField)
                        .align(AlignX.FILL)
                        .comment("Example: https://your-company.atlassian.net")
                }
                row("Username/Email:") {
                    cell(jiraUsernameField)
                        .align(AlignX.FILL)
                }
                row("API Token:") {
                    cell(jiraApiTokenField)
                        .align(AlignX.FILL)
                        .comment("Generate from Jira Settings → Security → API tokens")
                }
                row("Default Project Key:") {
                    cell(defaultProjectKeyField)
                        .align(AlignX.FILL)
                        .comment("Example: PROJ")
                }
                row("Default Issue Type:") {
                    cell(defaultIssueTypeField)
                        .align(AlignX.FILL)
                        .comment("Example: Task, Story, Bug")
                }
            }

            group("AI Configuration") {
                row("AI Provider:") {
                    cell(aiProviderField)
                        .align(AlignX.FILL)
                        .comment("openai or anthropic")
                }
                row("API Key:") {
                    cell(aiApiKeyField)
                        .align(AlignX.FILL)
                }
                row("Model:") {
                    cell(aiModelField)
                        .align(AlignX.FILL)
                        .comment("OpenAI: gpt-4-turbo, gpt-3.5-turbo | Anthropic: claude-3-haiku-20240307, claude-3-sonnet-20240229, claude-3-opus-20240229")
                }
            }

            group("Language Settings") {
                row("Default Language:") {
                    cell(defaultLanguageField)
                        .align(AlignX.FILL)
                        .comment("Language code: ko, en, ja, zh-CN, zh-TW, es, fr, de")
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

    override fun isModified(): Boolean {
        val state = settings.state
        return jiraUrlField.text != state.jiraUrl ||
                jiraUsernameField.text != state.jiraUsername ||
                String(jiraApiTokenField.password) != state.jiraApiToken ||
                defaultProjectKeyField.text != state.defaultProjectKey ||
                defaultIssueTypeField.text != state.defaultIssueType ||
                aiProviderField.text != state.aiProvider ||
                String(aiApiKeyField.password) != state.aiApiKey ||
                aiModelField.text != state.aiModel ||
                defaultLanguageField.text != state.defaultLanguage ||
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
        state.defaultProjectKey = defaultProjectKeyField.text
        state.defaultIssueType = defaultIssueTypeField.text
        state.aiProvider = aiProviderField.text
        state.aiApiKey = String(aiApiKeyField.password)
        state.aiModel = aiModelField.text
        state.defaultLanguage = defaultLanguageField.text
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
        defaultProjectKeyField.text = state.defaultProjectKey
        defaultIssueTypeField.text = state.defaultIssueType
        aiProviderField.text = state.aiProvider
        aiApiKeyField.text = state.aiApiKey
        aiModelField.text = state.aiModel
        defaultLanguageField.text = state.defaultLanguage
        rememberLastLanguage = state.rememberLastLanguage
        autoDetectLanguage = state.autoDetectLanguage
        includeDiffInDescription = state.includeDiffInDescription
        linkToCommit = state.linkToCommit
    }
}
