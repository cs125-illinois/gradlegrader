package edu.illinois.cs.cs125.gradlegrader.plugin

/**
 * Holds information about the best score and current commit for purposes of requiring commits after earning points.
 * @param lastSeenCommit the ID (e.g. Git hash) of the most recent commit
 * @param maxScore the best score achieved on the assignment from any commit
 * @param increased whether there are uncommitted score-increasing changes
 */
data class VcsScoreInfo(
        val lastSeenCommit: String? = null,
        val maxScore: Int = 0,
        val increased: Boolean = false
)