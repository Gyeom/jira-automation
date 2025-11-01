package com.github.gyeom.jiraautomation.model

import kotlinx.serialization.Serializable

/**
 * Epic 정보
 */
@Serializable
data class Epic(
    val id: String,
    val key: String,
    val self: String,
    val name: String,
    val summary: String,
    val done: Boolean = false,
    val color: EpicColor? = null
) {
    override fun toString(): String = "$key - $name"
}

/**
 * Epic 색상 정보
 */
@Serializable
data class EpicColor(
    val key: String
)

/**
 * Epic 검색 결과
 */
@Serializable
data class EpicSearchResult(
    val values: List<Epic>,
    val total: Int
)

/**
 * Epic 필드 (이슈 생성 시 사용)
 */
data class EpicLink(
    val key: String
)