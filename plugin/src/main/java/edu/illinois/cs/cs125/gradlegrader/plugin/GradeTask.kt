package edu.illinois.cs.cs125.gradlegrader.plugin

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import edu.illinois.cs.cs125.gradlegrader.annotations.Graded
import edu.illinois.cs.cs125.gradlegrader.annotations.Tag
import org.apache.commons.text.WordUtils
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.eclipse.jgit.lib.StoredConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.w3c.dom.Element
import java.io.File
import java.net.URLClassLoader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max

/**
 * A Gradle task to generate the grade report.
 */
open class GradeTask : DefaultTask() {

    /** Captured output logs.  */
    private var taskOutput = ""

    /** Graded test tasks. */
    private var gradedTests = mutableListOf<Test>()

    /** XML file written by checkstyle, or null if checkstyle is not configured. */
    private var checkstyleOutputFile: File? = null

    /** Collection of contributors/partners, or null if identification is not configured. */
    var contributors: List<String>? = null

    /** Configuration of the Git repository, or null if Git integration is off. */
    var gitConfig: StoredConfig? = null

    /** The ID of the last VCS commit, or null if Git integration is off. */
    var lastCommitId: String? = null

    /** Score-vs.-commit tracking information, or null if commit forcing is off. */
    var scoreInfo: VcsScoreInfo? = null

    /** Current checkpoint ID, or null if checkpointing is off. */
    var currentCheckpoint: String? = null

    /** Whether the repository is clean of changes. */
    var repoIsClean: Boolean = false

    /**
     * Sets up a listener to the specified task's output.
     * @param task the task to listen to
     */
    fun listenTo(task: Task) {
        val outputListener = StandardOutputListener { taskOutput += it }
        task.logging.addStandardOutputListener(outputListener)
        task.logging.addStandardErrorListener(outputListener)
        task.doLast {
            task.logging.removeStandardOutputListener(outputListener)
            task.logging.removeStandardErrorListener(outputListener)
        }
    }

    /**
     * Sets up the grader with information about a test task.
     * @param task the test task to add
     */
    fun gatherTestInfo(task: Test) {
        gradedTests.add(task)
    }

    /**
     * Sets up the grader with information about the (singular) checkstyle task.
     * @param task the checkstyle task
     */
    fun gatherCheckstyleInfo(task: Checkstyle) {
        if (checkstyleOutputFile != null) throw GradleException("checkstyle task already set")
        checkstyleOutputFile = task.reports.xml.destination
    }

    /**
     * Entry point for the task. Performs the grading.
     */
    @TaskAction
    @Suppress("unused")
    fun run() {
        val config = project.extensions.getByType(GradePolicyExtension::class.java)
        val exitManager = ExitManager(config)
        val documentBuilder = DocumentBuilderFactory.newInstance().apply { isValidating = false; isNamespaceAware = false }.newDocumentBuilder()
        val results = JsonObject()
        val scoringResults = JsonArray()
        var pointsPossible = 0
        var pointsEarned = 0
        var exitProcessWhenDone = false

        // Add custom tags
        config.reporting.tags.forEach {
            if (it.value is Int) {
                results.addProperty(it.key, it.value as Int)
            } else {
                results.addProperty(it.key, it.value as String)
            }
        }

        // Grade tests/projects
        val projectResults = JsonArray()
        gradedTests.forEach { task ->
            // Prepare a class loader that can access test suite classes
            val loader = URLClassLoader(task.classpath.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader)

            // Load the report XML files
            var compiled = false
            task.reports.junitXml.destination.listFiles { _, name -> name.endsWith(".xml") }?.forEach { file ->
                compiled = true
                val xml = documentBuilder.parse(file)
                val className = xml.documentElement.getAttribute("name")
                val testSuiteClass = loader.loadClass(className)
                val testcaseList = xml.documentElement.getElementsByTagName("testcase")
                (0 until testcaseList.length).map { n -> testcaseList.item(n) as Element }.forEach examineMethod@{
                    val testName = it.getAttribute("name")
                    val methodName = testName.substringBefore('[').substringBefore('(')
                    val testMethod = testSuiteClass!!.getMethod(methodName)
                    val gradedAnnotation = testMethod.getDeclaredAnnotation(Graded::class.java) ?: return@examineMethod
                    val methodResults = JsonObject()
                    testMethod.getAnnotationsByType(Tag::class.java).forEach { tag -> methodResults.addProperty(tag.name, tag.value) }
                    methodResults.addProperty("module", task.project.name)
                    methodResults.addProperty("className", className)
                    methodResults.addProperty("testCase", testName)
                    val passed = it.getElementsByTagName("failure").length == 0 && it.getElementsByTagName("skipped").length == 0
                    methodResults.addProperty("passed", passed)
                    methodResults.addProperty("pointsPossible", gradedAnnotation.points)
                    methodResults.addProperty("pointsEarned", if (passed) gradedAnnotation.points else 0)
                    if (!passed) {
                        methodResults.addProperty("failureStackTrace", it.getElementsByTagName("failure").item(0)?.textContent)
                    }
                    methodResults.addProperty("description",
                            if (gradedAnnotation.friendlyName.isEmpty()) methodName else gradedAnnotation.friendlyName)
                    methodResults.addProperty("explanation", testName + (if (passed) " passed" else " failed"))
                    methodResults.addProperty("type", "test")
                    scoringResults.add(methodResults)
                    pointsPossible += gradedAnnotation.points
                    pointsEarned += if (passed) gradedAnnotation.points else 0
                }
            }

            // Add module information
            val moduleResult = JsonObject()
            moduleResult.addProperty("name", task.project.name)
            moduleResult.addProperty("compiled", compiled)
            projectResults.add(moduleResult)
            if (!compiled) {
                val compileFailResult = JsonObject()
                compileFailResult.addProperty("module", task.project.name)
                compileFailResult.addProperty("description", "Compiler")
                compileFailResult.addProperty("pointsPossible", 0)
                compileFailResult.addProperty("pointsEarned", 0)
                compileFailResult.addProperty("explanation", "${task.project.name} didn't compile")
                compileFailResult.addProperty("type", "compileError")
                scoringResults.add(compileFailResult)
            }
        }
        results.add("modules", projectResults)

        // Load checkstyle XML
        if (config.checkstyle.enabled) {
            pointsPossible += config.checkstyle.points
            val checkstyleResults = JsonObject()
            val ran = checkstyleOutputFile!!.exists() && checkstyleOutputFile!!.length() > 0
            checkstyleResults.addProperty("ran", ran)
            var checkstylePoints = 0
            if (ran) {
                val xml = documentBuilder.parse(checkstyleOutputFile)
                val passed = xml.getElementsByTagName("error").length == 0
                checkstyleResults.addProperty("passed", passed)
                checkstyleResults.addProperty("explanation",
                        if (passed) "No checkstyle errors were reported" else "checkstyle found style issues")
                if (passed) checkstylePoints = config.checkstyle.points
            } else {
                checkstyleResults.addProperty("passed", false)
                checkstyleResults.addProperty("explanation", "checkstyle crashed")
                // If checkstyle crashed, it leaked the file handle
                // Future cleans will crash and fail the build unless we bring down this process
                exitProcessWhenDone = true
            }
            checkstyleResults.addProperty("description", "checkstyle")
            checkstyleResults.addProperty("pointsEarned", checkstylePoints)
            checkstyleResults.addProperty("pointsPossible", config.checkstyle.points)
            checkstyleResults.addProperty("type", "checkstyle")
            pointsEarned += checkstylePoints
            scoringResults.add(checkstyleResults)
        }

        // Scoring is done
        var showRawPointsEarned: Int? = null
        config.maxPoints?.let {
            pointsPossible = it
            if (pointsEarned > pointsPossible) {
                showRawPointsEarned = pointsEarned
                pointsEarned = pointsPossible
            }
        }
        results.add("scores", scoringResults)

        // Examine VCS state
        if (config.vcs.git) {
            val gitResults = JsonObject()
            val gitRemotes = JsonObject()
            gitConfig!!.getSubsections("remote").forEach {
                gitRemotes.addProperty(it, gitConfig!!.getString("remote", it, "url"))
            }
            gitResults.add("remotes", gitRemotes)
            val gitUser = JsonObject()
            gitUser.addProperty("name", gitConfig!!.getString("user", null, "name"))
            gitUser.addProperty("email", gitConfig!!.getString("user", null, "email"))
            gitResults.add("user", gitUser)
            gitResults.addProperty("head", lastCommitId)
            results.add("git", gitResults)
        }

        // Note score for commit requirement
        var needsCommit = false
        if (config.vcs.requireCommit && lastCommitId?.isNotEmpty() == true) {
            val checkpointScoreInfo = scoreInfo!!.getCheckpointInfo(currentCheckpoint) ?: VcsCheckpointScoreInfo(currentCheckpoint)
            needsCommit = (pointsEarned > checkpointScoreInfo.maxScore) && !repoIsClean
            val newScoreInfo = VcsCheckpointScoreInfo(currentCheckpoint, lastCommitId, max(pointsEarned, checkpointScoreInfo.maxScore), needsCommit)
            project.file("config").mkdir()
            project.file("config/.score.json").writeText(Gson().toJson(scoreInfo!!.withCheckpointInfoSet(newScoreInfo)))
        }

        // Add final properties
        results.addProperty("pointsEarned", pointsEarned)
        results.addProperty("pointsPossible", pointsPossible)
        results.addProperty("assignment", config.assignment)
        currentCheckpoint?.let { results.addProperty("checkpoint", it) }
        if (config.identification.enabled) {
            val contribArray = JsonArray()
            contributors!!.forEach { contribArray.add(it) }
            results.add("contributors", contribArray)
        }
        if (config.captureOutput) results.addProperty("output", taskOutput)

        // Make the JSON report(s)
        val resultJson = results.toString()
        config.reporting.jsonFile?.writeText(resultJson)
        if (config.reporting.printJson) println(resultJson)

        // Make the pretty report
        if (config.reporting.printPretty.enabled) {
            val line = "".padEnd(80, '-')
            println(line)
            config.reporting.printPretty.title?.let {
                println(it)
                println(line)
            }
            scoringResults.forEach {
                print(it.asJsonObject["description"].asString.take(30).padEnd(31, ' '))
                print(it.asJsonObject["pointsEarned"].asInt.toString().padEnd(5, ' '))
                println(it.asJsonObject["explanation"].asString)
            }
            println(line)
            if (config.reporting.printPretty.showTotal) {
                print("Total".padEnd(31, ' '))
                print(pointsEarned.toString().padEnd(5, ' '))
                showRawPointsEarned?.let { print("(the maximum, capped from $it)") }
                println()
                println(line)
            }
            config.reporting.printPretty.notes?.let {
                println(if (it.contains('\n')) it else WordUtils.wrap(it, 80))
                println(line)
            }
            if (needsCommit) {
                println("CONGRATULATIONS: Your changes increased your score from " +
                        "${scoreInfo!!.getCheckpointInfo(currentCheckpoint)?.maxScore ?: 0} to $pointsEarned!")
                println("Commit your work right away! The autograder will not run again until you do.")
                println(line)
            }
        }

        // Make the POST report (the only report that includes files)
        config.reporting.post.endpoint?.let {
            if (config.reporting.post.includeFiles.any()) {
                val filesResults = JsonArray()
                config.reporting.post.includeFiles.filter { file -> file.exists() }.forEach { file ->
                    val fileResult = JsonObject()
                    fileResult.addProperty("name", file.name)
                    fileResult.addProperty("path", file.absolutePath)
                    try {
                        fileResult.addProperty("data", file.readText())
                    } catch (ignored: Exception) { }
                    filesResults.add(fileResult)
                }
                results.add("files", filesResults)
            }
            val request = HttpPost(it)
            request.addHeader("Content-Type", "application/json")
            request.entity = StringEntity(results.toString()) // The version with the files included
            val client = HttpClientBuilder.create().build()
            try {
                client.execute(request)
            } catch (ignored: Exception) {} finally {
                client.close()
            }
        }

        // Exit as desired
        exitManager.finished(exitProcessWhenDone)
    }
}
