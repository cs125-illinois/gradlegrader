package edu.illinois.cs.cs125.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * Plugin containing our grade task.
 */
class GradePlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create('grade', GradePluginExtension, project)
        project.tasks.create('grade', GradeTask) {
            gradeConfigurationPath = extension.gradeConfigurationPath
            studentsConfigurationPath = extension.studentsConfigurationPath
        }
    }
}