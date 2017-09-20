package edu.illinois.cs.cs125.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

class GradePlugin implements Plugin<Project> {
    void apply(Project target) {
		target.task('grade', type: GradeTask)
    }
}

// vim: ts=4:sw=4:et:ft=groovy
