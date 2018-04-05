package edu.illinois.cs.cs125.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.compile.JavaCompile
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

        if (project.tasks.hasProperty('checkstyleMain')) {
            project.tasks.checkstyleMain.ignoreFailures = true
            project.tasks.checkstyleMain.outputs.upToDateWhen { false }
        }

        def gradeTask = project.tasks.create('grade', GradeTask) {}

        [project.tasks.clean,
         project.tasks.processResources,
         project.tasks.processTestResources].each { task ->
            gradeTask.addListener(task)
        }
        if (gradeConfiguration.checkstyle) {
            gradeTask.addListener(project.tasks.checkstyleMain)
        }

        gradeTask.dependsOn(project.tasks.clean)
        if (gradeConfiguration.checkstyle) {
            gradeTask.dependsOn(project.tasks.checkstyleMain)
            project.tasks.checkstyleMain.mustRunAfter(project.tasks.clean)
        }
        gradeTask.dependsOn(project.tasks.processResources)
        project.tasks.processResources.mustRunAfter(project.tasks.clean)
        gradeTask.dependsOn(project.tasks.processTestResources)
        project.tasks.processTestResources.mustRunAfter(project.tasks.clean)
        project.tasks.compileJava.enabled = false
        project.tasks.compileTestJava.enabled = false

        /*
         * We want to ignore errors in this block because we want to continue even if
         * compiling or testing fails.
         *
         * Note that the idea of executing tests is usually a no-no in Gradle.
         * But we have to do this if we want to give students partial credit,
         * since otherwise a single compilation failure will fail the entire build.
         */

        def testOutputDirectories = []
        def packagePath = ""
        if (gradeConfiguration.package) {
            packagePath = gradeConfiguration.package.replace(".", File.separator)
        }
        def mainResourcesDir = project.tasks.processResources.getDestinationDir()

        gradeConfiguration.files.each{info ->
            String compile, testCompile, test, name
            def sources
            try {
                compile = info.compile
                testCompile = info.test + "Test.java"
                test = info.test + "Test"
                name = info.test
            } catch (Exception ignored) {
                compile = info + ".java"
                testCompile = info + "Test.java"
                test = info + "Test"
                name = info
            }

            sources = project.sourceSets.main.java.srcDirs
            if (gradeConfiguration.package) {
                sources = sources.collect { String it -> new File(it, packagePath) }
            }
            def compileTask = project.tasks.create(name: "compile" + name, type: JavaCompile) {
                source = sources
                include compile
                classpath = project.sourceSets.main.compileClasspath
                destinationDir = project.sourceSets.main.java.outputDir
                options.failOnError = false
                options.debug = false
            }
            gradeTask.addListener(compileTask)

            sources = project.sourceSets.test.java.srcDirs
            if (gradeConfiguration.package) {
                sources = sources.collect { String it -> new File(it, packagePath ) }
            }
            def testCompileTask = project.tasks.create(name: "compileTest" + name, type: JavaCompile) {
                source = sources
                include testCompile
                classpath = project.sourceSets.test.compileClasspath
                destinationDir = project.sourceSets.test.java.outputDir
                options.failOnError = false
                options.debug = false
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
                testTask.jvmArgs("-Djava.security.policy=" +  (String) gradeConfiguration.secure)
                testTask.systemProperties(["main.sources": project.sourceSets.main.java.outputDir])
                testTask.systemProperties(["main.resources": mainResourcesDir])
            }
            testOutputDirectories.add(testTask.reports.getJunitXml().getDestination())
            gradeTask.dependsOn(testTask)
        }
        project.ext.set("testOutputDirectories", testOutputDirectories)
    }
}