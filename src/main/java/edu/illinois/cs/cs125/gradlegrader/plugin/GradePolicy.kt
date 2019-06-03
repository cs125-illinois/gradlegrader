@file:Suppress("unused")

package edu.illinois.cs.cs125.gradlegrader.plugin

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.specs.Spec
import java.io.File

/**
 * Gradle extension to hold the grading settings.
 * <p>
 * Methods are here to allow convenient use from Groovy.
 */
open class GradePolicyExtension {

    var assignment: String? = null
    var captureOutput: Boolean = false
    var checkstyle: CheckstylePolicy = CheckstylePolicy()
    var identification: IdentificationPolicy = IdentificationPolicy()
    var keepDaemon: Boolean = true
    var maxPoints: Int? = null
    var reporting: ReportingPolicy = ReportingPolicy()
    var subprojects: List<Project>? = null
    var vcs: VcsPolicy = VcsPolicy()

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
    fun vcs(action: Action<in VcsPolicy>) {
        action.execute(vcs)
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