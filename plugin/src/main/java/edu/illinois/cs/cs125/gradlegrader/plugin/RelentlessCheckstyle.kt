package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.plugins.quality.Checkstyle

/**
 * A checkstyle Gradle task that won't throw an exception if unable to parse a file.
 * That allows the Java compilation task to run and show its error message.
 */
open class RelentlessCheckstyle : Checkstyle() {

    /**
     * Entry point for the task.
     */
    override fun run() {
        try {
            super.run()
        } catch (e: Exception) {
            if (e.cause == null || !e.cause?.javaClass!!.name.endsWith("CheckstyleException")) {
                // It wasn't just a checkstyle failure
                throw RuntimeException(e)
            }
            System.err.print("Checkstyle crashed: ")
            e.printStackTrace(System.err)
            // The checkstyle task leaks the file handle when it crashes.
            // This scenario can be detected by looking for a zero-byte results XML file.
        }
    }

}
