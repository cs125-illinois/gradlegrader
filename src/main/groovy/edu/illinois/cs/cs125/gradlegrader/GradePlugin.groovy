package edu.illinois.cs.cs125.gradlegrader

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.yaml.snakeyaml.Yaml

/**
 * Plugin containing our grade task.
 */
class GradePlugin implements Plugin<Project> {

    void apply(Project project) {
        def extension = project.extensions.create('grade', GradePluginExtension, project)

        def yaml = new Yaml()
        def gradeConfiguration = yaml.load(project.file(extension.gradeConfigurationPath.get()).text)
        assert gradeConfiguration
        project.ext.set("gradeConfiguration", gradeConfiguration)

        if (gradeConfiguration.checkstyle && !project.tasks.hasProperty('checkstyleMain')) {
            throw new GradleException("checkstyle is configured for grading but not in build.gradle")
        }

        def gradeTask = project.tasks.create('grade', GradeTask) {}

        [project.tasks.processResources,
         project.tasks.processTestResources].each { task ->
            gradeTask.addListener(task)
        }

        if (gradeConfiguration.alwaysClean) {
            gradeTask.addListener(project.tasks.clean)
            gradeTask.dependsOn(project.tasks.clean)
        }
        if (gradeConfiguration.checkstyle) {
            gradeTask.addListener(project.tasks.checkstyleMain)
            gradeTask.dependsOn(project.tasks.checkstyleMain)
            project.tasks.checkstyleMain.mustRunAfter(project.tasks.clean)
        }

        gradeTask.dependsOn(project.tasks.processResources)
        project.tasks.processResources.mustRunAfter(project.tasks.clean)
        gradeTask.dependsOn(project.tasks.processTestResources)
        project.tasks.processTestResources.mustRunAfter(project.tasks.clean)

        def disableCompileTask = project.task('beforeGrade') {
            doLast {
                if (project.tasks.hasProperty('checkstyleMain')) {
                    project.tasks.checkstyleMain.ignoreFailures = true
                    project.tasks.checkstyleMain.outputs.upToDateWhen { false }
                }
                if (gradeConfiguration.files) {
                    project.tasks.compileJava.enabled = false
                    project.tasks.compileTestJava.enabled = false
                }
            }
        }
        gradeTask.dependsOn(disableCompileTask)
        project.tasks.checkstyleMain.mustRunAfter(disableCompileTask)
        project.tasks.compileJava.mustRunAfter(disableCompileTask)
        project.tasks.compileTestJava.mustRunAfter(disableCompileTask)

        def testOutputDirectories = []
        def packagePath = ""
        if (gradeConfiguration["package"]) {
            packagePath = gradeConfiguration["package"].replace(".", File.separator)
        }
        def mainResourcesDir = project.tasks.processResources.getDestinationDir()

        if (gradeConfiguration.files) {
            gradeConfiguration.files.each { info ->
                String[] compile
                String testCompile, test, name
                def sources
                try {
                    compile = info.compile
                    testCompile = info.test + "Test.java"
                    test = info.test + "Test"
                    name = compile.collect { String it -> it.replaceFirst(~/\.[^.]+$/, '') }.join()
                } catch (Exception ignored) {
                    compile = [info + ".java"]
                    testCompile = info + "Test.java"
                    test = info + "Test"
                    name = info
                }

                sources = project.sourceSets.main.java.srcDirs
                if (gradeConfiguration["package"]) {
                    sources = sources.collect { File it -> new File(it, packagePath) }
                }

                def compileTask = project.tasks.create(name: "compile" + name, type: JavaCompile) {
                    source = sources
                    include compile
                    classpath = project.sourceSets.main.compileClasspath
                    destinationDir = project.sourceSets.main.java.outputDir
                    options.failOnError = false
                    options.debug = false
                    options.incremental = false
                }
                gradeTask.addListener(compileTask)

                sources = project.sourceSets.test.java.srcDirs
                if (gradeConfiguration["package"]) {
                    sources = sources.collect { File it -> new File(it, packagePath) }
                }
                def testCompileTask = project.tasks.create(name: "compileTest" + name, type: JavaCompile) {
                    source = sources
                    include testCompile
                    classpath = project.sourceSets.test.compileClasspath
                    destinationDir = project.sourceSets.test.java.outputDir
                    options.failOnError = false
                    options.debug = false
                    options.incremental = false
                }
                testCompileTask.dependsOn(compileTask)
                gradeTask.addListener(testCompileTask)


                def showStreams = false
                if (gradeConfiguration.showStreams) {
                    showStreams = true
                }
                def testTask = project.tasks.create(name: "test" + name, type: Test) {
                    useTestNG() { useDefaultListeners = true }
                    testLogging.showStandardStreams = showStreams
                    reports.html.enabled = false
                    include "**" + packagePath + File.separator + test + "**"
                    ignoreFailures = true
                }
                testTask.dependsOn(testCompileTask)
                gradeTask.addListener(testTask)
                if (project.hasProperty("grade.secure") && gradeConfiguration.secure) {
                    gradeConfiguration.secureRun = true
                    testTask.jvmArgs("-Djava.security.manager=net.sourceforge.prograde.sm.ProGradeJSM")
                    testTask.jvmArgs("-Djava.security.policy=" + (String) gradeConfiguration.secure)
                    testTask.systemProperties(["main.sources": project.sourceSets.main.java.outputDir])
                    testTask.systemProperties(["main.resources": mainResourcesDir])
                }
                testOutputDirectories.add(testTask.reports.getJunitXml().getDestination())
                gradeTask.dependsOn(testTask)
            }
        } else if (gradeConfiguration.tasks) {
            gradeConfiguration.tasks.each { taskName ->
                def nameParts = taskName.split(":")
                def taskProject = false, tasks = []
                if (nameParts.size() == 1) {
                    taskProject = project
                    tasks = project.getTasksByName(nameParts[0], false)
                } else if (nameParts.size() == 2) {
                    taskProject = project.findProject(":" + nameParts[0])
                    tasks = taskProject.getTasksByName(nameParts[1], false)
                }
                if (!taskProject) {
                    throw new GradleException("couldn't find project for task description " + taskName)
                }
                if (tasks.size() != 1) {
                    throw new GradleException("task description " + taskName + " matched multiple tasks")
                }
                taskProject.tasks.withType(AbstractCompile).each { task ->
                    task.options.failOnError = false
                }
                def task = tasks[0]
                if (!(task instanceof AbstractTestTask)) {
                    throw new GradleException("task " + taskName + " is not a test task")
                }
                task.ignoreFailures = true
                gradeTask.addListener(task)
                gradeTask.dependsOn(task)
                testOutputDirectories.add(task.reports.getJunitXml().getDestination())
            }
        }
        print testOutputDirectories
        project.ext.set("testOutputDirectories", testOutputDirectories)
    }
}