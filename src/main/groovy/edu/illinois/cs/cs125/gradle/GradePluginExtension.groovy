package edu.illinois.cs.cs125.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property

class GradePluginExtension {
    final Property<String> gradeConfigurationPath

    GradePluginExtension(Project project) {
        gradeConfigurationPath = project.getObjects().property(String)
        gradeConfigurationPath.set('config/grade.yaml')
    }
}
