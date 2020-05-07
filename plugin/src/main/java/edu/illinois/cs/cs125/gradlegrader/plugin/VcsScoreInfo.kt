package edu.illinois.cs.cs125.gradlegrader.plugin

/**
 * Holds information about the project's best scores and commits for purposes of requiring commits after earning points.
 * @param checkpoints the list of per-checkpoint score information
 */
data class VcsScoreInfo(
    val checkpoints: List<VcsCheckpointScoreInfo>
) {
    fun getCheckpointInfo(checkpoint: String?): VcsCheckpointScoreInfo? {
        return checkpoints.firstOrNull { it.checkpoint == checkpoint }
    }
    fun withCheckpointInfoSet(info: VcsCheckpointScoreInfo): VcsScoreInfo {
        return VcsScoreInfo(checkpoints.filter { it.checkpoint != info.checkpoint } + listOf(info))
    }
}

/**
 * Holds information about the best score and current commit on one checkpoint.
 * @param checkpoint the checkpoint ID or null if checkpointing is not enabled
 * @param lastSeenCommit the ID (e.g. Git hash) of the most recent commit
 * @param maxScore the best score achieved on the assignment from any commit
 * @param increased whether there are uncommitted score-increasing changes
 */
data class VcsCheckpointScoreInfo(
    val checkpoint: String? = null,
    val lastSeenCommit: String? = null,
    val maxScore: Int = 0,
    val increased: Boolean = false
)
