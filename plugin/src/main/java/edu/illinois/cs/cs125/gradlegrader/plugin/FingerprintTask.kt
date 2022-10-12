package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.math.BigInteger
import java.security.MessageDigest

fun String.fingerprint(): String = MessageDigest.getInstance("MD5").let { md ->
    val filtered = lines().filter {
        !it.startsWith("// md5: ")
    }.joinToString("\n")
    return BigInteger(1, md.digest(filtered.toByteArray())).toString(16).padStart(32, '0')
}

abstract class FingerprintTask : DefaultTask() {
    init {
        group = "Publishing"
        description = "Fingerprint test suites."
    }

    @InputFiles
    val inputFiles: FileTree = project.fileTree("src/test").also {
        it.include("**/*Test.java", "**/*Test.kt")
    }

    @TaskAction
    fun fingerprint() {
        inputFiles.forEach {
            println("$it -> // md5: ${it.readText().fingerprint()}")
        }
    }
}
