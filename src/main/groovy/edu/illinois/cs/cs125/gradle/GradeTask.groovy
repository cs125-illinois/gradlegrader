package edu.illinois.cs.cs125.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.PropertyState
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.logging.StandardOutputListener

import org.yaml.snakeyaml.Yaml
import groovy.json.*
import static groovy.io.FileType.FILES

import static javax.xml.xpath.XPathConstants.*

import java.nio.file.Paths

import javax.xml.xpath.*
import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*

/**
 * Class implementing our Gradle grade task.
 */
class GradeTask extends DefaultTask {

    @Internal
    final PropertyState<String> gradeConfigurationPath = project.property(String)

    @Internal
    final PropertyState<String> studentsConfigurationPath = project.property(String)

    /**
     * Open an XML file given a path.
     *
     * @param path to the file to open.
     * @return a DOM object representing the data in the file.
     */
    def openXML(path) {
        return DOMBuilder.parse(new StringReader(path.text), false, false).documentElement
    }

    def fill(text, width=78, prefix='') {
        width = width - prefix.size()
        def out = []
        List words = text.replaceAll("\n", " ").split(" ")
        while (words) {
            def line = ''
            while (words) {
                if (line.size() + words[0].size() + 1 > width) break
                if (line) line += ' '
                line += words[0]
                words = words.tail()
            }
            out += prefix + line
        }
        out.join("\n")
    }

    def addListener(task, listener) {
        task.logging.addStandardErrorListener(listener)
        task.logging.addStandardOutputListener(listener)

        task.doLast {
            task.logging.removeStandardOutputListener(listener)
            task.logging.removeStandardErrorListener(listener)
        }
    }
    /**
     * Create a new grade task.
     */
    GradeTask() {
        /*
         * Ensure that checkstyle doesn't fail the entire build, and always get rerun.
         */
        project.tasks.checkstyleMain.setIgnoreFailures(true)
        project.tasks.checkstyleMain.outputs.upToDateWhen { false }
    }

    /**
     * Grade an assignment.
     */
    @TaskAction
    def gradeAssignment() throws Exception {

        Yaml yaml = new Yaml();
        def gradeConfiguration = yaml.load(project.file(gradeConfigurationPath.get()).text)
        gradeConfiguration.students = yaml.load(project.file(studentsConfigurationPath.get()).text)
        gradeConfiguration.timestamp = System.currentTimeMillis()

        def taskOutput = ''
        def listener = { taskOutput += it } as StandardOutputListener
        [project.tasks.clean,
            project.tasks.checkstyleMain,
            project.tasks.processResources,
            project.tasks.processTestResources].each { task ->
            addListener(task, listener)
        }
        project.tasks.clean.execute()
        project.tasks.checkstyleMain.execute()
        def mainResourcesDir = project.tasks.processResources.getDestinationDir()
        project.tasks.processResources.execute()
        project.tasks.processTestResources.execute()

        /*
         * We want to ignore errors in this block because we want to continue even if
         * compiling or testing fails.
         *
         * Note that the idea of executing tests is usually a no-no in Gradle.
         * But we have to do this if we want to give students partial credit,
         * since otherwise a single compilation failure will fail the entire build.
         */

        def testOutputDirectories = []
        gradeConfiguration.files.each{info ->
            def compile, testCompile, test
            try {
                compile = info.compile
                testCompile = info.test + "Test.java"
                test = info.test + "Test"
            } catch (Exception e) {
                compile = info + ".java"
                testCompile = info + "Test.java"
                test = info + "Test"
            }
            try {
                def compileTask = project.tasks.create(name: "compile" + name, type: JavaCompile) {
                    source = project.sourceSets.main.java.srcDirs
                    include compile
                    classpath = project.sourceSets.main.compileClasspath
                    destinationDir = project.sourceSets.main.java.outputDir
                }
                addListener(compileTask, listener)
                compileTask.execute()
            } catch (Exception e) {}
            try {
                def testCompileTask = project.tasks.create(name: "compileTest" + name, type: JavaCompile) {
                    source = project.sourceSets.test.java.srcDirs
                    include testCompile
                    classpath = project.sourceSets.test.compileClasspath
                    destinationDir = project.sourceSets.test.java.outputDir
                }
                addListener(testCompileTask, listener)
                testCompileTask.execute()
            } catch (Exception e) {}
            try {
                def testTask = project.tasks.create(name: "test" + name, type: Test, dependsOn: 'compileTest' + name) {
                    useTestNG() { useDefaultListeners = true }
                    reports.html.enabled = false
                    include "**" + test + "**"
                }
                addListener(testTask, listener)
                if (project.hasProperty("grade.secure") && gradeConfiguration.secure) {
                    gradeConfiguration.secureRun = true;
                    testTask.jvmArgs("-Djava.security.manager=net.sourceforge.prograde.sm.ProGradeJSM")
                    testTask.jvmArgs("-Djava.security.policy=" + gradeConfiguration.secure)
                    testTask.systemProperties(["main.sources": project.sourceSets.main.java.outputDir])
                    testTask.systemProperties(["main.resources": mainResourcesDir])
                }
                testOutputDirectories.add(testTask.reports.getJunitXml().getDestination())
                testTask.execute()
            } catch (Exception e) {}
        }

        /*
         * Investigate checkstyle results.
         */
        def toKeep = []
        if (gradeConfiguration.checkstyle) {
            def checkstyleResultsPath = project.tasks.checkstyleMain.getReports().getXml().getDestination()
            try {
                def checkstyleResults = openXML(checkstyleResultsPath)
                gradeConfiguration.checkstyle.remove('missing')
                use (DOMCategory) {
                    gradeConfiguration.checkstyle.selectors.each { checkstyleSelector ->
                        if (checkstyleResults.xpath(checkstyleSelector.selector, BOOLEAN)) {
                            toKeep.add(checkstyleSelector)
                        }
                    }
                }
                def toRemove = gradeConfiguration.checkstyle.selectors - toKeep
                toRemove.each { checkstyleSelector ->
                    gradeConfiguration.checkstyle.selectors.remove(checkstyleSelector)
                }
            } catch (Exception e) {
                gradeConfiguration.checkstyle.selectors = [
                    gradeConfiguration.checkstyle.missing
                ]
            }
        }

        /*
         * Investigate TestNG results.
         *
         * We merge all TestNG results into a single XML file.
         * This simplifies selector design, since they can all match in a single file.
         */
        def mergedXML = new XmlSlurper().parseText("<testsuites></testsuites>")

        def testResultsDirectory = project.tasks.test.reports.getJunitXml().getDestination()
        toKeep = []
        testOutputDirectories.each{ testOutputDirectory ->
            if (testOutputDirectory.exists()) {
                testOutputDirectory.eachFileMatch(~/.*\.xml/) { testResultsPath ->
                    def testResults = new XmlSlurper().parse(testResultsPath)
                    mergedXML.appendNode(testResults)
                }
            }
        }
        mergedXML = DOMBuilder.parse(new StringReader(groovy.xml.XmlUtil.serialize(mergedXML)), false, false).documentElement
        use (DOMCategory) {
            gradeConfiguration.test.selectors.each { testSelector ->
                if (mergedXML.xpath(testSelector.selector, BOOLEAN)) {
                    toKeep.add(testSelector)
                }
            }
        }

        def toRemove = gradeConfiguration.test.selectors - toKeep
        toRemove.each { testSelector ->
            gradeConfiguration.test.selectors.remove(testSelector)
        }

        def totalScore = 0
        println ""
        println "".padRight(78, "-")
        println gradeConfiguration.name + " Grade Summary"
        println "".padRight(78, "-")
        gradeConfiguration.each{ unused, v ->
            if (v instanceof Map && v.containsKey('selectors')) {
                v.selectors.each{ selector ->
                    print selector.name.padRight(20)
                    print selector.score.toString().padLeft(8)
                    print "     " + selector.message
                    print '\n'
                    totalScore += selector.score
                }
            }
        }
        println "".padRight(78, "-")
        print "Total Points".padRight(20)
        print totalScore.toString().padLeft(8)
        print '\n'
        println "".padRight(78, "-")

        if (gradeConfiguration.notes) {
            println fill(gradeConfiguration.notes)
            println "".padRight(78, "-")
        }
        if (project.hasProperty("grade.capture")) {
            gradeConfiguration.output = taskOutput
        }
        gradeConfiguration.totalScore = totalScore

        if (gradeConfiguration.reporting) {
            def destination = project.findProperty("grade.reporting") ?: gradeConfiguration.reporting.default;
            if (destination == "post" && gradeConfiguration.reporting.post) {
                def gradePost = new HttpPost(gradeConfiguration.reporting.post)
                gradePost.addHeader("content-type", "application/json")
                gradePost.setEntity(new StringEntity(JsonOutput.toJson(gradeConfiguration)))

                def client = HttpClientBuilder.create().build()
                try {
                    def response = client.execute(gradePost)
                } catch (Exception e) { }
            } else if (destination == "directory" && gradeConfiguration.reporting.containsKey("directory")) {
                def filename = Paths.get(gradeConfiguration.reporting.directory, gradeConfiguration.students.join("_") + ".json")
                def file = new File(filename.toString())
                def writer = file.newWriter()
                writer << JsonOutput.toJson(gradeConfiguration);
                writer.close();
            }
        }
    }
}
