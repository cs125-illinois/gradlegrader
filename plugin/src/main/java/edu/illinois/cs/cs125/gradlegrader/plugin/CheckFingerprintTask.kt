package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

fun File.checkFingerprint(base: File) {
    val contents = readText()
    val fileFingerprint = contents.lines()
        .find { it.startsWith("// md5: ") }
        ?.removePrefix("// md5: ")
        ?.split(" ")
        ?.firstOrNull()
        ?.trim()
        ?: throw GradleException(
            "Can't find fingerprint for file ${relativeTo(base).path}. " +
                "Restore from Git or download again.",
        )
    val contentFingerprint = contents.fingerprint()
    if (fileFingerprint != contentFingerprint) {
        throw GradleException(
            "Fingerprint mismatch for test file ${relativeTo(base).path}. " +
                "Undo your changes, restore from Git, or download again.",
        )
    }
}

abstract class CheckFingerprintTask : DefaultTask() {
    init {
        group = "Verification"
        description = "Check test suite fingerprints."
    }

    @InputFiles
    val inputFiles: FileTree = project.fileTree("src/test").also {

        it.include("**/*Test.java", "**/*Test.kt")
    }

    @TaskAction
    fun check() {
        val base = project.rootDir
        inputFiles.forEach {
            it.checkFingerprint(base)
        }
    }
}
