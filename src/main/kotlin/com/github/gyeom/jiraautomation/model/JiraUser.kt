package com.github.gyeom.jiraautomation.model

import kotlinx.serialization.Serializable

/**
 * Jira 사용자 정보
 */
@Serializable
data class JiraUser(
    val accountId: String,
    val displayName: String,
    val emailAddress: String? = null,
    val avatarUrls: Map<String, String> = emptyMap(),
    val active: Boolean = true,
    val self: String? = null  // REST API URL
) {
    /**
     * 표시용 이름 (이메일이 있으면 함께 표시)
     */
    fun getDisplayText(): String {
        return if (!emailAddress.isNullOrEmpty()) {
            "$displayName ($emailAddress)"
        } else {
            displayName
        }
    }

    /**
     * 아바타 URL 가져오기 (크기별)
     */
    fun getAvatarUrl(size: String = "32x32"): String? {
        return avatarUrls[size] ?: avatarUrls["48x48"] ?: avatarUrls.values.firstOrNull()
    }

    override fun toString(): String = getDisplayText()
}

/**
 * 사용자 검색 결과
 */
@Serializable
data class UserSearchResult(
    val users: List<JiraUser>,
    val total: Int
)

/**
 * 현재 사용자 정보
 */
@Serializable
data class CurrentUser(
    val accountId: String,
    val displayName: String,
    val emailAddress: String?,
    val avatarUrls: Map<String, String>,
    val active: Boolean,
    val locale: String?,
    val timeZone: String?
) {
    fun toJiraUser(): JiraUser {
        return JiraUser(
            accountId = accountId,
            displayName = displayName,
            emailAddress = emailAddress,
            avatarUrls = avatarUrls,
            active = active
        )
    }
}