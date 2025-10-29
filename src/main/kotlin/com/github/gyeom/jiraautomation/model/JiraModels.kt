package com.github.gyeom.jiraautomation.model

data class JiraIssueRequest(
    val fields: JiraIssueFields
)

data class JiraIssueFields(
    val project: JiraProject,
    val summary: String,
    val description: JiraDescription?,
    val issuetype: JiraIssueType,
    val assignee: JiraAssignee? = null,
    val priority: JiraPriority? = null
)

data class JiraProject(
    val key: String
)

data class JiraDescription(
    val type: String = "doc",
    val version: Int = 1,
    val content: List<JiraContent>
)

data class JiraContent(
    val type: String,
    val content: List<JiraTextContent>? = null,
    val attrs: Map<String, Any>? = null
)

data class JiraTextContent(
    val type: String,
    val text: String? = null,
    val marks: List<JiraTextMark>? = null
)

data class JiraTextMark(
    val type: String,
    val attrs: Map<String, String>? = null
)

data class JiraIssueType(
    val name: String
)

data class JiraAssignee(
    val accountId: String? = null,
    val emailAddress: String? = null
)

data class JiraPriority(
    val id: String? = null,
    val name: String? = null
)

data class JiraIssueResponse(
    val id: String,
    val key: String,
    val self: String
)

data class JiraErrorResponse(
    val errorMessages: List<String>?,
    val errors: Map<String, String>?
)

enum class OutputLanguage(val displayName: String, val code: String) {
    KOREAN("한국어", "ko"),
    ENGLISH("English", "en"),
    JAPANESE("日本語", "ja"),
    CHINESE_SIMPLIFIED("简体中文", "zh-CN"),
    CHINESE_TRADITIONAL("繁體中文", "zh-TW"),
    SPANISH("Español", "es"),
    FRENCH("Français", "fr"),
    GERMAN("Deutsch", "de");

    companion object {
        fun fromCode(code: String) = values().find { it.code == code } ?: ENGLISH
    }
}

data class GeneratedTicket(
    val title: String,
    val description: String
)

// Project list response models
data class JiraProjectListResponse(
    val id: String,
    val key: String,
    val name: String,
    val projectTypeKey: String? = null
)

// Issue type models
data class JiraIssueTypeResponse(
    val self: String? = null,
    val id: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val subtask: Boolean = false
)

// Priority response model
data class JiraPriorityResponse(
    val self: String? = null,
    val id: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null
)

// AI Provider models
data class AIProviderInfo(
    val provider: String,
    val models: List<String>
)
