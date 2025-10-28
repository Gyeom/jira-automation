package com.github.gyeom.jiraautomation.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository

@Service(Service.Level.PROJECT)
class DiffAnalysisService(private val project: Project) {

    data class DiffAnalysisResult(
        val filesChanged: Int,
        val linesAdded: Int,
        val linesDeleted: Int,
        val fileList: List<String>,
        val diffContent: String,
        val branchName: String?,
        val commits: List<CommitInfo>
    )

    data class CommitInfo(
        val hash: String,
        val message: String,
        val author: String
    )

    fun analyzeUncommittedChanges(): DiffAnalysisResult? {
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.allChanges

        if (changes.isEmpty()) {
            return null
        }

        return analyzeChanges(changes.toList(), getCurrentBranch())
    }

    fun analyzeChanges(changes: List<Change>, branchName: String?): DiffAnalysisResult {
        var linesAdded = 0
        var linesDeleted = 0
        val fileList = mutableListOf<String>()
        val diffContentBuilder = StringBuilder()

        for (change in changes) {
            val filePath = getFilePath(change)
            fileList.add(filePath)

            val beforeContent = change.beforeRevision?.content ?: ""
            val afterContent = change.afterRevision?.content ?: ""

            val diff = computeDiff(beforeContent, afterContent)
            linesAdded += diff.added
            linesDeleted += diff.deleted

            diffContentBuilder.append("File: $filePath\n")
            diffContentBuilder.append(diff.content)
            diffContentBuilder.append("\n---\n\n")
        }

        return DiffAnalysisResult(
            filesChanged = fileList.size,
            linesAdded = linesAdded,
            linesDeleted = linesDeleted,
            fileList = fileList,
            diffContent = diffContentBuilder.toString(),
            branchName = branchName,
            commits = emptyList()
        )
    }

    private fun getFilePath(change: Change): String {
        return change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: "unknown"
    }

    private data class DiffStats(
        val added: Int,
        val deleted: Int,
        val content: String
    )

    private fun computeDiff(before: String, after: String): DiffStats {
        val beforeLines = before.split("\n")
        val afterLines = after.split("\n")

        var added = 0
        var deleted = 0
        val diffContent = StringBuilder()

        // Simple diff algorithm (can be improved with proper diff library)
        val maxLines = maxOf(beforeLines.size, afterLines.size)

        for (i in 0 until maxLines) {
            val beforeLine = beforeLines.getOrNull(i)
            val afterLine = afterLines.getOrNull(i)

            when {
                beforeLine == null && afterLine != null -> {
                    added++
                    diffContent.append("+ $afterLine\n")
                }
                beforeLine != null && afterLine == null -> {
                    deleted++
                    diffContent.append("- $beforeLine\n")
                }
                beforeLine != afterLine -> {
                    deleted++
                    added++
                    diffContent.append("- $beforeLine\n")
                    diffContent.append("+ $afterLine\n")
                }
                else -> {
                    diffContent.append("  $beforeLine\n")
                }
            }
        }

        return DiffStats(added, deleted, diffContent.toString())
    }

    private fun getCurrentBranch(): String? {
        return try {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull()
            repository?.currentBranch?.name
        } catch (e: Exception) {
            null
        }
    }

    fun getGitRepository(): GitRepository? {
        return try {
            GitUtil.getRepositoryManager(project).repositories.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun formatDiffSummary(result: DiffAnalysisResult): String {
        return buildString {
            appendLine("## Code Changes Summary")
            appendLine()
            appendLine("- **Files Changed**: ${result.filesChanged}")
            appendLine("- **Lines Added**: +${result.linesAdded}")
            appendLine("- **Lines Deleted**: -${result.linesDeleted}")

            if (result.branchName != null) {
                appendLine("- **Branch**: ${result.branchName}")
            }

            appendLine()
            appendLine("### Modified Files")
            result.fileList.forEach { file ->
                appendLine("- `$file`")
            }

            if (result.commits.isNotEmpty()) {
                appendLine()
                appendLine("### Recent Commits")
                result.commits.forEach { commit ->
                    appendLine("- ${commit.hash.substring(0, 7)}: ${commit.message}")
                }
            }
        }
    }
}
