package io.github.sanitised.st

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

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

    /** 端口上已有服务在接受连接(用于判定本地 ST 服务真正就绪,而非仅进程存活)。 */
    fun isTcpPortAccepting(port: Int, timeoutMs: Int = 500): Boolean {
        if (port !in 1..65535) return false
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), timeoutMs)
            }
        }.isSuccess
    }
}
