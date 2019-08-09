@file:SuppressWarnings("NewApi")

package edu.illinois.cs.cs125.gradlegrader.plugin

import com.google.gson.Gson
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.io.File
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

        fun findTestTasks(): List<Test> {
            return findSubprojects().map { it.getTasksByName("testDebugUnitTest", false).stream().findAny()
                    .orElse(it.getTasksByName("test", false).stream().findAny()
                    .orElse(null)) as Test? ?: exitManager.fail("Couldn't find a test task for ${it.name}") }
        }

        fun findCompileTasks(): List<JavaCompile> {
            return findSubprojects().flatMap { it.tasks.withType(JavaCompile::class.java) }
        }

        val checkstyleTask = project.tasks.register("relentlessCheckstyle", RelentlessCheckstyle::class.java).get()
        project.tasks.register("grade", GradeTask::class.java) { gradeTask ->
            // Depend on all subprojects' cleaning and tests
            gradeTask.dependsOn(findTestTasks())
            val cleanTasks = findSubprojects().map { it.tasks.getByName("clean") }
            gradeTask.dependsOn(cleanTasks)

            // Require cleaning before compilation
            findCompileTasks().forEach { it.mustRunAfter(cleanTasks) }

            // Depend on checkstyle
            val reconfTask = project.tasks.getByName("prepareForGrading")
            if (config.checkstyle.enabled) {
                checkstyleTask.mustRunAfter(reconfTask)
                checkstyleTask.mustRunAfter(cleanTasks)
                gradeTask.dependsOn(checkstyleTask)
                gradeTask.gatherCheckstyleInfo(checkstyleTask)
            }

            // Depend on reconfiguration
            gradeTask.dependsOn(reconfTask)
        }
        project.task("prepareForGrading").doLast {
            // Find the grade task (can't realize it with get() because that would configure it too early)
            val gradeTask = project.tasks.getByName("grade") as GradeTask

            // Check contributors file
            if (config.identification.enabled) {
                val txtFile = config.identification.txtFile!!
                if (!txtFile.exists()) {
                    exitManager.fail("Missing identification file: ${txtFile.absolutePath}")
                }
                val partners = Files.readAllLines(txtFile.toPath()).filter { it.isNotBlank() }
                if (!config.identification.countLimit.isSatisfiedBy(partners.size)) {
                    exitManager.fail("Invalid number of contributors (${partners.size}) in identification file: ${txtFile.absolutePath}")
                }
                partners.forEach {
                    if (!config.identification.validate.isSatisfiedBy(it)) exitManager.fail("Invalid contributor format: $it")
                }
                gradeTask.contributors = partners
            }

            // Check VCS
            if (config.vcs.git) {
                val gitRepo = try {
                    FileRepositoryBuilder().setMustExist(true).addCeilingDirectory(File(".")).findGitDir().build()
                } catch (_: Exception) { exitManager.fail("Grader Git integration is enabled but no Git root was found") }
                gradeTask.gitConfig = gitRepo.config
                val lastCommit = gitRepo.resolve(Constants.HEAD).name
                gradeTask.lastCommitId = lastCommit
                if (config.vcs.requireCommit) {
                    var scoreInfo = VcsScoreInfo()
                    try {
                        scoreInfo = Gson().fromJson(project.file("config/.score.json").readText(), VcsScoreInfo::class.java)
                    } catch (ignored: Exception) { }
                    val status = Git.open(gitRepo.workTree).status().call()
                    val clean = (status.added.size + status.changed.size + status.removed.size + status.modified.size + status.missing.size) == 0
                    if (scoreInfo.increased && scoreInfo.lastSeenCommit == lastCommit && !clean) {
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
                checkstyleTask.setProperty("ignoreFailures", true)
                checkstyleTask.outputs.upToDateWhen { false }
                checkstyleTask.source(findSubprojects().map { "${it.projectDir}/src/main" })
                checkstyleTask.setIncludes(config.checkstyle.include)
                checkstyleTask.setExcludes(config.checkstyle.exclude)
                checkstyleTask.configFile = config.checkstyle.configFile ?: exitManager.fail("checkstyle.configFile not specified")
                checkstyleTask.classpath = project.files()
                gradeTask.listenTo(checkstyleTask)
            }

            // Configure the test tasks
            findTestTasks().forEach {
                gradeTask.listenTo(it)
                if (!project.hasProperty("grade.ignoreproperties")) {
                    config.systemProperties.forEach { (prop, value) ->
                        it.systemProperty(prop, value)
                    }
                }
                if (project.hasProperty("grade.testfilter")) {
                    it.setTestNameIncludePatterns(mutableListOf(project.property("grade.testfilter") as String))
                    it.filter.isFailOnNoMatchingTests = false
                }
                it.setProperty("ignoreFailures", true)
                it.outputs.upToDateWhen { false }
                gradeTask.gatherTestInfo(it)
            }

            // Configure compilation tasks
            findCompileTasks().forEach {
                gradeTask.listenTo(it)
                it.options.isFailOnError = false
                it.outputs.upToDateWhen { false }
            }
        }.dependsOn(project.tasks.register("clearBuildDir", Delete::class.java) { delTask ->
            delTask.delete = setOf(project.buildDir)
        }.get())
    }

}
