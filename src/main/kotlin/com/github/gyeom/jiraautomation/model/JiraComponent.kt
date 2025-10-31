package com.github.gyeom.jiraautomation.model

import kotlinx.serialization.Serializable

/**
 * Jira 컴포넌트 정보
 */
@Serializable
data class JiraComponent(
    val id: String,
    val name: String,
    val description: String? = null,
    val self: String? = null,
    val project: String? = null,
    val assigneeType: String? = null,
    val lead: JiraComponentLead? = null
)

/**
 * 컴포넌트 리더 정보
 */
@Serializable
data class JiraComponentLead(
    val accountId: String? = null,
    val displayName: String? = null,
    val emailAddress: String? = null
)

/**
 * 컴포넌트 참조 (이슈 생성 시 사용)
 */
data class ComponentReference(
    val id: String
)