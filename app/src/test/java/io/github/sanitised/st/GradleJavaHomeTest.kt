package io.github.sanitised.st

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleJavaHomeTest {
    @Test
    fun gradleUsesJava21Runtime() {
        val gradleProperties = Properties().apply {
            File("../gradle.properties").inputStream().use { load(it) }
        }
        val javaHome = gradleProperties.getProperty("org.gradle.java.home").orEmpty()

        assertTrue("org.gradle.java.home should point to a Java runtime", javaHome.isNotBlank())
        assertTrue("Configured Java runtime should exist", File(javaHome, "bin/java").canExecute())

        val releaseFile = File(javaHome, "release")
        assertTrue("Java runtime should include a release file", releaseFile.exists())
        val javaVersionLine = releaseFile.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") }
        assertEquals("JAVA_VERSION=\"21.0.11\"", javaVersionLine)
    }
}
