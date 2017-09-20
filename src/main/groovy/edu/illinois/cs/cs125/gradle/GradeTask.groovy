package edu.illinois.cs.cs125.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import org.yaml.snakeyaml.Yaml
import groovy.json.*
import static groovy.io.FileType.FILES

import static javax.xml.xpath.XPathConstants.*
import javax.xml.xpath.*
import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*

class GradeTask extends DefaultTask {

    def configure() {
        tasks.checkstyleMain.setIgnoreFailures(true)
        tasks.checkstyleMain.outputs.upToDateWhen { false }
    }

    def doLast() {
        // Load configuration
        Yaml yaml = new Yaml()
        def gradeConfiguration = yaml.load(file('config/grade/grade.yaml').text)
        gradeConfiguration.students = yaml.load(file('config/grade/students.yaml').text)
        gradeConfiguration.timestamp = System.currentTimeMillis()

        tasks.clean.execute()
        try {
            tasks.checkstyleMain.execute()
        } catch (Exception e) { }

        def testOutputDirectories = []
        gradeConfiguration.files.each{name ->
            try {
                tasks.create(name: "compile" + name, type: JavaCompile) {
                    source = sourceSets.main.java.srcDirs
                    include name + ".java"
                    classpath = sourceSets.main.compileClasspath
                    destinationDir = sourceSets.main.output.classesDir
                }.execute()
            } catch (Exception e) { }
            try {
                tasks.create(name: "compileTest" + name, type: JavaCompile) {
                    source = sourceSets.test.java.srcDirs
                    include name + "Test.java"
                    classpath = sourceSets.test.compileClasspath
                    destinationDir = sourceSets.test.output.classesDir
                }.execute()
            } catch (Exception e) { }
            try {
                def test = tasks.create(name: "test" + name, type: Test, dependsOn: 'compileTest' + name) {
                    useTestNG() {
                        useDefaultListeners = true
                    }
                    reports.html.enabled = false
                    include "**" + name + "Test**"
                }
                testOutputDirectories.add(test.reports.getJunitXml().getDestination())
                test.execute()
            } catch (Exception e) { }
        }

        // Investigate checkstyle results
        def toKeep = []
        if (gradeConfiguration.containsKey('checkstyle')) {
            def checkstyleResultsPath = tasks.checkstyleMain.getReports().getXml().getDestination()
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
                gradeConfiguration.checkstyle.selectors = [ gradeConfiguration.checkstyle.missing ]
            }
        }

        def mergedXML = new XmlSlurper().parseText("<testsuites></testsuites>")

        // Investigate TestNG results
        def testResultsDirectory = tasks.test.reports.getJunitXml().getDestination()
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

        gradeConfiguration.totalScore = totalScore

        def gradePost = new HttpPost(gradeConfiguration.post)
        gradePost.addHeader("content-type", "application/json")
        gradePost.setEntity(new StringEntity(JsonOutput.toJson(gradeConfiguration)))

        def client = HttpClientBuilder.create().build()
        try {
            def response = client.execute(gradePost)
        } catch (Exception e) { }
    }
}

// vim: ts=4:sw=4:et:ft=groovy
