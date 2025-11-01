package com.github.gyeom.jiraautomation.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "JiraTicketCreatorSettings",
    storages = [Storage("jiraTicketCreator.xml")]
)
class JiraSettingsState : PersistentStateComponent<JiraSettingsState.State> {

    data class State(
        var jiraUrl: String = "https://company.atlassian.net",
        var jiraApiToken: String = "",
        var jiraUsername: String = "",
        var defaultProjectKey: String = "PROJECT_KEY",
        var defaultIssueType: String = "Task",
        var aiProvider: String = "anthropic",
        var aiApiKey: String = "",
        var aiModel: String = "claude-3-haiku-20240307",
        var defaultLanguage: String = "ko",
        var rememberLastLanguage: Boolean = true,
        var autoDetectLanguage: Boolean = false,
        var lastUsedLanguage: String = "ko",
        var includeDiffInDescription: Boolean = true,
        var linkToCommit: Boolean = true,

        // Last used values for ticket creation dialog
        var lastUsedProjectKey: String = "",
        var lastUsedIssueType: String = "",
        var lastUsedPriorityId: String = "",
        var lastUsedEpicKey: String = "",
        var lastUsedLabel: String = "",
        var lastUsedComponentId: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): JiraSettingsState =
            project.service<JiraSettingsState>()
    }
}
