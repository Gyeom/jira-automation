package com.github.gyeom.jiraautomation.model

// ComponentReference is defined in JiraComponent.kt

data class JiraIssueRequest(
    val fields: JiraIssueFields
)

data class JiraIssueFields(
    val project: JiraProject,
    val summary: String,
    val description: JiraDescription?,
    val issuetype: JiraIssueType,
    val assignee: JiraAssignee? = null,
    val reporter: JiraReporter? = null,
    val priority: JiraPriority? = null,
    val parent: ParentIssueRef? = null,      // Parent issue for subtasks
    val customfield_10014: String? = null,  // Epic Link field (customfield ID may vary)
    val customfield_10020: Long? = null,     // Sprint field (customfield ID may vary)
    val labels: List<String>? = null,        // Labels
    val components: List<ComponentReference>? = null,  // Components
    val timetracking: JiraTimeTracking? = null,  // Time tracking
    val customfield_10016: Double? = null,   // Story Points (customfield ID may vary)
    val duedate: String? = null,             // Due date in YYYY-MM-DD format
    val customfield_10015: String? = null    // Start date (customfield ID may vary)
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

data class JiraReporter(
    val accountId: String? = null,
    val emailAddress: String? = null
)

data class JiraPriority(
    val id: String? = null,
    val name: String? = null
)

data class JiraTimeTracking(
    val originalEstimate: String? = null,    // e.g., "3h", "2d", "1w"
    val remainingEstimate: String? = null,   // e.g., "1h 30m"
    val timeSpent: String? = null           // e.g., "30m"
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

// Recent issue model for displaying in Tool Window
data class RecentIssue(
    val key: String,
    val summary: String,
    val status: String,
    val created: String,
    val issueType: String,
    val projectKey: String,
    val projectName: String,
    val url: String,
    val priority: String? = null,
    val statusCategory: String? = null  // new, indeterminate, done
)

// Status category enum for filtering
enum class StatusCategory(val key: String, val displayName: String, val colorHex: String) {
    TODO("new", "To Do", "#42526E"),           // blue-gray
    IN_PROGRESS("indeterminate", "In Progress", "#FFAB00"),  // yellow
    DONE("done", "Done", "#00875A");            // green

    companion object {
        fun fromKey(key: String?) = values().find { it.key == key }
    }
}

// Parent issue reference for creating subtasks
data class ParentIssueRef(
    val key: String
)

// Parent issue details for validation
data class ParentIssue(
    val key: String,
    val id: String,
    val summary: String,
    val projectKey: String,
    val issueType: String,
    val status: String,
    val epicKey: String? = null  // Epic Link from parent
)

// Issue transition models for status changes
data class IssueTransition(
    val id: String,
    val name: String,
    val to: TransitionStatus? = null,
    val hasScreen: Boolean = false,
    val fields: Map<String, TransitionField>? = null
)

data class TransitionStatus(
    val id: String,
    val name: String
)

data class TransitionField(
    val required: Boolean,
    val name: String,
    val fieldId: String,
    val schema: TransitionFieldSchema? = null,
    val allowedValues: List<Any>? = null
)

data class TransitionFieldSchema(
    val type: String,
    val system: String? = null
)
