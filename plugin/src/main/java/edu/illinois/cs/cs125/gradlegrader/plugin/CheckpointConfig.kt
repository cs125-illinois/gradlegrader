package edu.illinois.cs.cs125.gradlegrader.plugin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CheckpointConfig(
    val checkpoint: String,
)
