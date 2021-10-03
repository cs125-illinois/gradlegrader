@file:SuppressWarnings("NewApi")

package edu.illinois.cs.cs125.gradlegrader.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.Gson
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.nio.file.Files

/**
 * The gradlegrader Gradle plugin.
 */
@Suppress("unused")
class GradleGraderPlugin : Plugin<Project> {

    /**
     * Applies the plugin to the project.
     * @param project the Gradle project to apply to
     */
    override fun apply(project: Project) {
        val config = project.extensions.create("gradlegrader", GradePolicyExtension::class.java)
        val exitManager = ExitManager(config)

        fun findSubprojects(): List<Project> {
            return config.subprojects ?: listOf(project)
        }

        val compileTasks = mutableSetOf<JavaCompile>()
        val testTasks = mutableMapOf<Project, Test>()
        var currentCheckpoint: String? = null

        val checkstyleTask = project.tasks.register("relentlessCheckstyle", RelentlessCheckstyle::class.java).get()
        val detektTask = project.tasks.register("ourDetekt", Detekt::class.java).get()
        val gradeTask: GradeTask = project.tasks.register("grade", GradeTask::class.java).get()
        val reconfTask = project.task("prepareForGrading").doLast {
            // Check projects' test tasks
            findSubprojects().forEach { subproject ->
                if (!testTasks.containsKey(subproject)) {
                    exitManager.fail("Couldn't find a test task for project ${subproject.path}")
                }
            }

            // Check contributors file
            if (config.identification.enabled) {
                val txtFile = config.identification.txtFile!!
                if (!txtFile.exists()) {
                    exitManager.fail("Missing contributor identification file: ${txtFile.absolutePath}")
                }
                val partners = Files.readAllLines(txtFile.toPath()).filter { it.isNotBlank() }
                if (!config.identification.countLimit.isSatisfiedBy(partners.size)) {
                    exitManager.fail(
                        config.identification.message
                            ?: "Invalid number of contributors (${partners.size}) in identification file: ${txtFile.absolutePath}"
                    )
                }
                partners.forEach {
                    if (!config.identification.validate.isSatisfiedBy(it)) exitManager.fail(
                        config.identification.message
                            ?: "Invalid contributor format: $it"
                    )
                }
                gradeTask.contributors = partners
            }

            // Check VCS
            if (config.vcs.git) {
                val gitRepo = try {
                    val ceiling = project.rootProject.projectDir.parentFile // Go an extra level up to work around a JGit bug
                    FileRepositoryBuilder().setMustExist(true).addCeilingDirectory(ceiling).findGitDir(project.projectDir).build()
                } catch (_: Exception) { exitManager.fail("Grader Git integration is enabled but the project isn't a Git repository.") }
                gradeTask.gitConfig = gitRepo.config
                val lastCommit = gitRepo.resolve(Constants.HEAD).name
                gradeTask.lastCommitId = lastCommit
                if (config.vcs.requireCommit) {
                    var scoreInfo = VcsScoreInfo(listOf())
                    try {
                        val loadedInfo = Gson().fromJson(project.rootProject.file(".score.json").readText(), VcsScoreInfo::class.java)
                        @Suppress("SENSELESS_COMPARISON") // Possible for checkpoints to be null if loaded by Gson
                        if (loadedInfo.checkpoints != null) scoreInfo = loadedInfo
                    } catch (ignored: Exception) { }
                    val checkpointScoreInfo = scoreInfo.getCheckpointInfo(currentCheckpoint) ?: VcsCheckpointScoreInfo(currentCheckpoint)
                    val status = Git.open(gitRepo.workTree).status().call()
                    val clean = (status.added.size + status.changed.size + status.removed.size + status.modified.size + status.missing.size) == 0
                    if (checkpointScoreInfo.increased && checkpointScoreInfo.lastSeenCommit == lastCommit && !clean) {
                        exitManager.fail("The autograder will not run until you commit the changes that increased your score.")
                    }
                    gradeTask.scoreInfo = scoreInfo
                    gradeTask.repoIsClean = clean
                }
            }

            // Configure checkstyle
            if (config.checkstyle.enabled) {
                val checkstyleConfig = project.extensions.getByType(CheckstyleExtension::class.java)
                checkstyleConfig.toolVersion = config.checkstyle.version
                checkstyleConfig.reportsDir = project.file("build/reports/checkstyle")
                checkstyleTask.ignoreFailures = true
                checkstyleTask.outputs.upToDateWhen { false }
                checkstyleTask.source(findSubprojects().map { "${it.projectDir}/src/main" })
                checkstyleTask.setIncludes(config.checkstyle.include)
                checkstyleTask.setExcludes(config.checkstyle.exclude)
                checkstyleTask.configFile = config.checkstyle.configFile ?: exitManager.fail("checkstyle.configFile not specified")
                checkstyleTask.classpath = project.files()
                gradeTask.listenTo(checkstyleTask)
            }

            if (config.detekt.enabled) {
                val detektConfig = project.extensions.getByType(DetektExtension::class.java)
                detektTask.ignoreFailures = true
                detektTask.outputs.upToDateWhen { false }
                detektTask.source(detektConfig.source)
                detektTask.buildUponDefaultConfig = detektConfig.buildUponDefaultConfig
                detektTask.config.setFrom(detektConfig.config)
            }

            // Configure the test tasks
            testTasks.values.forEach {
                gradeTask.listenTo(it)
                if (!project.hasProperty("grade.ignoreproperties")) {
                    config.systemProperties.forEach { (prop, value) ->
                        it.systemProperty(prop, value)
                    }
                }
                if (project.hasProperty("grade.testfilter")) {
                    it.setTestNameIncludePatterns(mutableListOf(project.property("grade.testfilter") as String))
                    it.filter.isFailOnNoMatchingTests = false
                } else if (currentCheckpoint != null) {
                    config.checkpointing.testConfigureAction.accept(currentCheckpoint!!, it)
                }
                it.setProperty("ignoreFailures", true)
                it.outputs.upToDateWhen { false }
                gradeTask.gatherTestInfo(it)
            }

            // Configure compilation tasks
            compileTasks.forEach {
                gradeTask.listenTo(it)
                it.options.isFailOnError = false
                it.outputs.upToDateWhen { false }
            }
        }

        // Logic that depends on all projects having been evaluated
        val onAllProjectsReady = {
            val cleanTasks = findSubprojects().map { it.tasks.getByName("clean") }
            if (config.forceClean) {
                // Require a clean first
                gradeTask.dependsOn(cleanTasks)
            }

            // Depend on checkstyle
            if (config.checkstyle.enabled) {
                checkstyleTask.mustRunAfter(reconfTask)
                checkstyleTask.mustRunAfter(cleanTasks)
                gradeTask.dependsOn(checkstyleTask)
                gradeTask.gatherCheckstyleInfo(checkstyleTask)
            }

            if (config.detekt.enabled) {
                detektTask.mustRunAfter(reconfTask)
                detektTask.mustRunAfter(cleanTasks)
                gradeTask.dependsOn(detektTask)
                gradeTask.gatherDetektInfo(detektTask)
            }

            // Get subproject tasks
            findSubprojects().forEach { subproject ->
                // Clean before compile
                subproject.tasks.withType(JavaCompile::class.java) { compile ->
                    compile.mustRunAfter(cleanTasks)
                    compile.mustRunAfter(reconfTask)
                    compileTasks.add(compile)
                }

                // Depend on tests
                subproject.tasks.withType(Test::class.java) { test ->
                    if (test.name in setOf("test", "testDebugUnitTest")) {
                        testTasks[subproject] = test
                        test.mustRunAfter(cleanTasks)
                        test.mustRunAfter(reconfTask)
                        gradeTask.dependsOn(test)
                    }
                }
            }
        }

        // Finish setup once all projects have been evaluated and tasks have been created
        project.afterEvaluate {
            if (config.forceClean) {
                reconfTask.dependsOn(
                    project.tasks.register("clearBuildDir", Delete::class.java) { delTask ->
                        delTask.delete = setOf(project.buildDir)
                    }.get()
                )
            }
            gradeTask.dependsOn(reconfTask)
            if (config.checkpointing.yamlFile != null) {
                val configLoader = ObjectMapper(YAMLFactory()).also { it.registerKotlinModule() }
                val checkpointConfig = configLoader.readValue<CheckpointConfig>(config.checkpointing.yamlFile!!)
                currentCheckpoint = checkpointConfig.checkpoint
                gradeTask.currentCheckpoint = currentCheckpoint
            }
            val evalPending = findSubprojects().toMutableList()
            evalPending.remove(project)
            if (evalPending.size == 0) {
                onAllProjectsReady()
            } else {
                evalPending.toList().forEach { subproject ->
                    subproject.afterEvaluate {
                        evalPending.remove(subproject)
                        if (evalPending.size == 0) {
                            onAllProjectsReady()
                        }
                    }
                }
            }
        }
    }
}
