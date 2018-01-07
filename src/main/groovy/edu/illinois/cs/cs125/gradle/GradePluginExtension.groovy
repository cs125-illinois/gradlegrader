package edu.illinois.cs.cs125.gradle

import org.gradle.api.Project
import org.gradle.api.provider.PropertyState

class GradePluginExtension {
    final PropertyState<String> gradeConfigurationPath

    GradePluginExtension(Project project) {
        gradeConfigurationPath = project.property(String)
        gradeConfigurationPath.set('config/grade.yaml')
    }
}
