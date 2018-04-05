package edu.illinois.cs.cs125.gradle

import groovy.json.JsonOutput
import groovy.xml.DOMBuilder
import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory
import org.apache.commons.validator.routines.EmailValidator
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import static javax.xml.xpath.XPathConstants.BOOLEAN

/**
 * Class implementing our Gradle grade task.
 */
class GradeTask extends DefaultTask {

    @Internal String taskOutput = ''

    /**
     * Open an XML file given a path.
     *
     * @param path to the file to open.
     * @return a DOM object representing the data in the file.
     */
    def static openXML(path) {
        return DOMBuilder.parse(new StringReader(path.text), false, false).documentElement
    }

    def static fill(text, width=78, prefix='') {
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

    def reportConfiguration(gradeConfiguration) {
        if (gradeConfiguration.reporting) {
            if (gradeConfiguration.reporting.used == "post") {
                def gradePost = new HttpPost((String) gradeConfiguration.reporting.post)
                gradePost.addHeader("content-type", "application/json")
                gradePost.setEntity(new StringEntity(JsonOutput.toJson(gradeConfiguration)))

                def client = HttpClientBuilder.create().build()
                try {
                    client.execute(gradePost)
                } catch (Exception ignored) { }
            } else if (gradeConfiguration.reporting.used == "file") {
                def filename = project.findProperty("grade.reporting.file") ?: gradeConfiguration.reporting.file
                def file = new File(filename.toString())
                def writer = file.newWriter()
                writer << JsonOutput.toJson(gradeConfiguration)
                writer.close()
            }
        }
    }

    def addListener(task) {
        task.logging.addStandardErrorListener(listener)
        task.logging.addStandardOutputListener(listener)

        task.doLast {
            task.logging.removeStandardOutputListener(listener)
            task.logging.removeStandardErrorListener(listener)
        }
    }

    def listener = { taskOutput += it } as StandardOutputListener

    /**
     * Grade an assignment.
     */
    @TaskAction
    @SuppressWarnings("unused")
    def gradeAssignment() {

        def gradeConfiguration = project.gradeConfiguration
        assert gradeConfiguration

        /*
         * Determine where to write output.
         */
        if (gradeConfiguration.reporting) {
            def destination
            if (project.hasProperty("grade.reporting")) {
                destination = project.findProperty("grade.reporting")
            } else if (project.hasProperty("grade.reporting.file")) {
                destination = "file"
                gradeConfiguration.reporting.file = project.findProperty("grade.reporting.file")
            } else {
                if (gradeConfiguration.reporting.size() == 1) {
                    destination = gradeConfiguration.reporting.keySet().toArray()[0]
                } else {
                    destination = gradeConfiguration.reporting.default
                }
            }
            assert destination
            assert gradeConfiguration.reporting[destination]
            gradeConfiguration.reporting.used = destination
        }

        /*
         * If configured, try to extract some information about their repository.
         */
        if (gradeConfiguration.vcs && gradeConfiguration.vcs.git) {
            try {
                def gitRepository = new FileRepositoryBuilder()
                        .setMustExist(true)
                        .addCeilingDirectory(new File("."))
                        .findGitDir()
                        .build()
                def config = gitRepository.config
                def remoteURLs = [:]
                config.getSubsections('remote').each { remote ->
                    remoteURLs[remote] = config.getString('remote', remote, 'url')
                }
                gradeConfiguration.vcs.git = [
                        remotes: remoteURLs,
                        user: [
                                name : config.getString("user", null, "name"),
                                email: config.getString("user", null, "email")
                        ],
                        head: gitRepository.resolve(Constants.HEAD).name
                ]

            } catch (Exception e) { }
        }
        if (gradeConfiguration.students) {
            def location = gradeConfiguration.students.location
            def emails = []
            try {
                def emailString = new File(location).text.trim()
                if (emailString.length() == 0) {
                    throw new Exception()
                }
                emails = emailString.split("\n")
                if (emails.length == 0) {
                    throw new Exception()
                }
            } catch (Exception e) {
                if (gradeConfiguration.students.require) {
                    System.err.println "FAILURE: Before running the autograder, please add your email address to " +
                            location + "."
                    gradeConfiguration.missingEmail = true
                    reportConfiguration(gradeConfiguration)
                    throw new GradleException("missing email address: please fix " + location)
                }
            }
            if (gradeConfiguration.students.require) {
                try {
                    if (gradeConfiguration.students.count && emails.length != gradeConfiguration.students.count) {
                        throw new GradleException("wrong email count: should only have " +
                                gradeConfiguration.students.count + " email")
                    }
                    emails.each { email ->
                        if (!EmailValidator.getInstance().isValid(email)) {
                            System.err.println "FAILURE: " + email +
                                    " is not a valid email address. Please fix " + location + "."
                            throw new GradleException("invalid email address")
                        }
                        if (gradeConfiguration.students.suffix) {
                            def emailParts = email.split("@")
                            if (!(emailParts[0] + gradeConfiguration.students.suffix).equals(email)) {
                                System.err.println "FAILURE: " + email + " is not an " +
                                        gradeConfiguration.students.suffix + " email address. " +
                                        "Please fix " + location + "."
                                throw new GradleException("incorrect email address: please fix " + location)
                            }
                        }
                    }
                } catch (GradleException e) {
                    gradeConfiguration.badEmail = true
                    reportConfiguration(gradeConfiguration)
                    throw (e)
                } catch (Exception e) {
                    System.err << "FAILURE: failure validating email addresses " + e +
                            ". Please fix " + location + "."
                    gradeConfiguration.badEmail = true
                    reportConfiguration(gradeConfiguration)
                    throw new GradleException("email validation failure: please fix " + location)
                }
                gradeConfiguration.students.people = emails
            }
        }
        gradeConfiguration.timestamp = System.currentTimeMillis()

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
        project.testOutputDirectories.each{ testOutputDirectory ->
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
        gradeConfiguration.testXML = XmlUtil.serialize(mergedXML)

        def toRemove = gradeConfiguration.test.selectors - toKeep
        toRemove.each { testSelector ->
            gradeConfiguration.test.selectors.remove(testSelector)
        }

        /*
         * Complete adding information to the output object.
         */
        def totalScore = 0
        gradeConfiguration.each{ key, v ->
            if (key != 'scoring' && v instanceof Map && v.containsKey('selectors')) {
                v.selectors.each{ selector ->
                    totalScore += selector.score
                }
            }
        }
        if (project.hasProperty("grade.capture")) {
            gradeConfiguration.output = taskOutput
        }
        gradeConfiguration.totalScore = totalScore

        println ""
        println "".padRight(78, "-")
        println gradeConfiguration.name + " Grade Summary"
        println "".padRight(78, "-")
        gradeConfiguration.each{ key, v ->
            if (key != 'scoring' && v instanceof Map && v.containsKey('selectors')) {
                v.selectors.each{ selector ->
                    print selector.name.padRight(20)
                    print selector.score.toString().padLeft(8)
                    print "     " + selector.message
                    print '\n'
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

        reportConfiguration(gradeConfiguration)
    }
}
