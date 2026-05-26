package io.github.sanitised.st

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class NetworkSecurityConfigTest {
    @Test
    fun manifestReferencesNetworkSecurityConfig() {
        val manifest = parseXml(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals(
            "@xml/network_security_config",
            application.getAttributeNS(ANDROID_NS, "networkSecurityConfig")
        )
        assertFalse(
            "Use the network security config instead of enabling all cleartext traffic.",
            application.getAttributeNS(ANDROID_NS, "usesCleartextTraffic").toBoolean()
        )
    }

    @Test
    fun networkSecurityConfigAllowsLoopbackCleartextOnly() {
        val configFile = File("src/main/res/xml/network_security_config.xml")
        assertTrue("Missing network_security_config.xml", configFile.exists())

        val config = parseXml(configFile)
        val baseConfigs = config.getElementsByTagName("base-config")
        assertTrue("Expected a base-config", baseConfigs.length > 0)
        assertEquals(
            "false",
            (baseConfigs.item(0) as Element).getAttribute("cleartextTrafficPermitted")
        )

        val allowedCleartextDomains = mutableSetOf<String>()
        val domainConfigs = config.getElementsByTagName("domain-config")
        for (index in 0 until domainConfigs.length) {
            val domainConfig = domainConfigs.item(index) as Element
            if (domainConfig.getAttribute("cleartextTrafficPermitted") != "true") continue

            val domains = domainConfig.getElementsByTagName("domain")
            for (domainIndex in 0 until domains.length) {
                allowedCleartextDomains += domains.item(domainIndex).textContent.trim()
            }
        }

        assertTrue("127.0.0.1 must be allowed for the embedded SillyTavern server", "127.0.0.1" in allowedCleartextDomains)
        assertTrue("localhost should be allowed as a loopback alias", "localhost" in allowedCleartextDomains)
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
