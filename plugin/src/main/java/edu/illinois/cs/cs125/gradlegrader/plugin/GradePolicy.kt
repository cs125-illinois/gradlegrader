@file:Suppress("unused")

package edu.illinois.cs.cs125.gradlegrader.plugin

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.util.function.BiConsumer

/**
 * Gradle extension to hold the grading settings.
 * <p>
 * Methods are here to allow convenient use from Groovy.
 */
open class GradePolicyExtension {

    var assignment: String? = null
    var captureOutput: Boolean = false
    var checkpointing: CheckpointPolicy = CheckpointPolicy()
    var checkstyle: CheckstylePolicy = CheckstylePolicy()
    var forceClean: Boolean = true
    var identification: IdentificationPolicy = IdentificationPolicy()
    var keepDaemon: Boolean = true
    var maxPoints: Int? = null
    var reporting: ReportingPolicy = ReportingPolicy()
    var subprojects: List<Project>? = null
    var systemProperties: MutableMap<String, String> = mutableMapOf()
    var vcs: VcsPolicy = VcsPolicy()

    fun checkpoint(action: Action<in CheckpointPolicy>) {
        action.execute(checkpointing)
    }
    fun checkstyle(action: Action<in CheckstylePolicy>) {
        checkstyle.enabled = true
        action.execute(checkstyle)
    }
    fun identification(action: Action<in IdentificationPolicy>) {
        identification.enabled = true
        action.execute(identification)
    }
    fun reporting(action: Action<in ReportingPolicy>) {
        action.execute(reporting)
    }
    fun subprojects(vararg projects: Project) {
        subprojects = projects.asList()
    }
    fun systemProperty(name: String, value: String) {
        systemProperties[name] = value
    }
    fun vcs(action: Action<in VcsPolicy>) {
        action.execute(vcs)
    }

}

/**
 * Class to hold checkpoint (sub-assignment) settings.
 */
open class CheckpointPolicy {
    var yamlFile: File? = null
    var testConfigureAction: BiConsumer<String, Test> = BiConsumer { _, _ -> }

    fun configureTests(action: (String, Test) -> Unit) {
        testConfigureAction = BiConsumer { checkpoint, test -> action(checkpoint, test) }
    }
    fun configureTests(action: BiConsumer<String, Test>) {
        testConfigureAction = action
    }
    fun configureTests(action: Closure<Void>) {
        testConfigureAction = BiConsumer { checkpoint, test -> action.call(checkpoint, test) }
    }
}

/**
 * Class to hold checkstyle settings.
 */
open class CheckstylePolicy {
    var enabled: Boolean = false
    var configFile: File? = null
    var exclude: List<String> = listOf("**/gen/**", "**/R.java", "**/BuildConfig.java")
    var include: List<String> = listOf("**/*.java")
    var points: Int = 0
    var version: String = "8.18"

    fun exclude(vararg patterns: String) {
        exclude = patterns.toList()
    }
    fun include(vararg patterns: String) {
        include = patterns.toList()
    }
}

/**
 * Class to hold identification settings.
 */
open class IdentificationPolicy {
    var enabled: Boolean = false
    var txtFile: File? = null
    var validate: Spec<String> = Spec { true }
    var countLimit: Spec<Int> = Spec { it == 1 }
    var message: String? = null
}

/**
 * Class to hold reporting settings.
 * <p>
 * Functions that take Closures are needed so Groovy targets the right object when setting properties.
 */
open class ReportingPolicy {
    var jsonFile: File? = null
    var post: PostReportingPolicy = PostReportingPolicy()
    var printJson: Boolean = false
    var printPretty: PrettyReportingPolicy = PrettyReportingPolicy()
    var tags: MutableMap<String, Any> = mutableMapOf()

    fun post(action: Action<in PostReportingPolicy>) {
        action.execute(post)
    }
    fun post(action: Closure<in PostReportingPolicy>) {
        action.delegate = post
        action.run()
    }
    fun printPretty(action: Action<in PrettyReportingPolicy>) {
        action.execute(printPretty)
    }
    fun printPretty(action: Closure<in PrettyReportingPolicy>) {
        action.delegate = printPretty
        action.run()
    }
    fun tag(name: String, value: String) {
        tags[name] = value
    }
    fun tag(name: String, value: Int) {
        tags[name] = value
    }
}

/**
 * Class to hold POST report settings.
 */
open class PostReportingPolicy {
    var endpoint: String? = null
    var includeFiles: List<File> = listOf()

    fun includeFiles(vararg files: File) {
        includeFiles = files.asList()
    }
    fun includeFiles(files: ConfigurableFileCollection) {
        includeFiles = files.toList()
    }
}

/**
 * Class to hold settings for the pretty standard-out report.
 */
open class PrettyReportingPolicy {
    var enabled: Boolean = true
    var notes: String? = null
    var showTotal: Boolean = true
    var title: String? = null
}

/**
 * Class to hold VCS inspection settings.
 */
open class VcsPolicy {
    var git: Boolean = false
    var requireCommit: Boolean = false
}
