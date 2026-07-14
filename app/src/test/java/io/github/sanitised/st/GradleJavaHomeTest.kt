package io.github.sanitised.st

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GradleJavaHomeTest {
    /**
     * 校验开发机上 gradle.properties 里配置的 JDK。
     *
     * 该配置是开发机专用路径（见 CLAUDE.md），在 CI / 其它机器上并不存在，
     * 因此当 org.gradle.java.home 未配置或指向的路径不可用时，本测试自动跳过
     * （CI 会通过 `sed` 去掉这一行并改用环境 JDK）。只有当配置存在且有效时，
     * 才强制校验它指向 JDK 21.0.11，防止本地误配。
     */
    @Test
    fun gradleUsesJava21RuntimeWhenConfigured() {
        val gradleProperties = Properties().apply {
            File("../gradle.properties").inputStream().use { load(it) }
        }
        val javaHome = gradleProperties.getProperty("org.gradle.java.home").orEmpty()

        // CI / 未配置该属性的环境：无从校验，直接跳过。
        assumeTrue("org.gradle.java.home 未配置，跳过本地 JDK 校验", javaHome.isNotBlank())
        assumeTrue(
            "org.gradle.java.home 指向的路径不存在（可能是 CI 或扩展升级后失效），跳过",
            File(javaHome, "bin/java").canExecute()
        )

        val releaseFile = File(javaHome, "release")
        assertTrue("Java runtime should include a release file", releaseFile.exists())
        val javaVersionLine = releaseFile.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") }
        assertEquals("JAVA_VERSION=\"21.0.11\"", javaVersionLine)
    }
}
