package com.github.gyeom.jiraautomation.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Sprint 정보
 */
@Serializable
data class Sprint(
    val id: Long,
    val self: String,
    val state: String,  // Changed from SprintState enum to String to handle case-insensitive values
    val name: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val completeDate: String? = null,
    val originBoardId: Long? = null,
    val goal: String? = null
)

/**
 * Sprint 상태
 */
@Serializable
enum class SprintState {
    FUTURE,     // 계획된 스프린트
    ACTIVE,     // 진행 중인 스프린트
    CLOSED      // 완료된 스프린트
}

/**
 * Sprint 검색 결과
 */
@Serializable
data class SprintSearchResult(
    val values: List<Sprint>,
    val total: Int
)

/**
 * Board 정보
 */
@Serializable
data class Board(
    val id: Long,
    val self: String,
    val name: String,
    val type: String  // scrum or kanban
)