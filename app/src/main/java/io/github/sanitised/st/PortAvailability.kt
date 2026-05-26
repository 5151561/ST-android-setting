package io.github.sanitised.st

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

object PortAvailability {
    fun isTcpPortAvailable(port: Int): Boolean {
        if (port !in 1..65535) return false
        return runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1)
            }
        }.isSuccess
    }
}
