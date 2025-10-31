package com.github.gyeom.jiraautomation.model

import kotlinx.serialization.Serializable

/**
 * Jira Issue Link 정보
 */
@Serializable
data class JiraIssueLink(
    val id: String? = null,
    val type: IssueLinkType,
    val inwardIssue: LinkedIssue? = null,
    val outwardIssue: LinkedIssue? = null
)

/**
 * Issue Link Type 정보
 */
@Serializable
data class IssueLinkType(
    val id: String? = null,
    val name: String,
    val inward: String,     // e.g., "is blocked by"
    val outward: String,    // e.g., "blocks"
    val self: String? = null
)

/**
 * Linked Issue 정보
 */
@Serializable
data class LinkedIssue(
    val id: String,
    val key: String,
    val self: String,
    val fields: LinkedIssueFields? = null
)

/**
 * Linked Issue Fields
 */
@Serializable
data class LinkedIssueFields(
    val summary: String? = null,
    val status: IssueStatus? = null,
    val priority: JiraPriority? = null,
    val issuetype: JiraIssueType? = null
)

/**
 * Issue Status
 */
@Serializable
data class IssueStatus(
    val self: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val name: String,
    val id: String
)

/**
 * Issue Link Request (for creating links)
 */
data class CreateIssueLinkRequest(
    val type: IssueLinkTypeRef,
    val inwardIssue: IssueRef,
    val outwardIssue: IssueRef,
    val comment: LinkComment? = null
)

/**
 * Issue Link Type Reference
 */
data class IssueLinkTypeRef(
    val name: String  // e.g., "Blocks", "Relates", "Duplicate"
)

/**
 * Issue Reference
 */
data class IssueRef(
    val key: String  // e.g., "PROJECT-123"
)

/**
 * Link Comment
 */
data class LinkComment(
    val body: String
)