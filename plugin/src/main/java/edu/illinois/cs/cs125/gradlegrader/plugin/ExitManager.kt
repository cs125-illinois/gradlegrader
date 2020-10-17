package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.GradleException
import java.lang.RuntimeException

/**
 * Manages the exit of the task/process according to configuration.
 * @param policy the grading policy from Gradle
 */
class ExitManager(private val policy: GradePolicyExtension) {

    /**
     * Exits the task with an error.
     * @param message the error message to display
     * @param forceExit whether to definitely bring down the process regardless of configuration
     */
    fun fail(message: String, forceExit: Boolean = false): Nothing {
        if (policy.keepDaemon && !forceExit) {
            throw GradleException(message)
        } else {
            System.err.println("FAILURE: $message")
            finished()
            throw RuntimeException("UNREACHABLE")
        }
    }

    /**
     * Exits the process if configured, otherwise does nothing.
     * @param forceExit whether to definitely bring down the process regardless of configuration
     */
    fun finished(forceExit: Boolean = false) {
        if (!policy.keepDaemon || forceExit) {
            System.out.flush()
            System.err.flush()
            System.exit(0)
        }
    }
}
