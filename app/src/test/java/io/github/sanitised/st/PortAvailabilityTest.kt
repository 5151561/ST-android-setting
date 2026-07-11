package io.github.sanitised.st

import java.net.ServerSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortAvailabilityTest {
    @Test
    fun tcpPortAvailabilityReflectsOccupiedAndReleasedPorts() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        assertFalse(PortAvailability.isTcpPortAvailable(port))

        socket.close()

        assertTrue(PortAvailability.isTcpPortAvailable(port))
    }

    @Test
    fun tcpPortAcceptingReflectsListeningAndClosedPorts() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        assertTrue(PortAvailability.isTcpPortAccepting(port))

        socket.close()

        assertFalse(PortAvailability.isTcpPortAccepting(port))
    }
}
