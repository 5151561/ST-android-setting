package io.github.sanitised.st

object SillyTavernUrl {
    fun localWebUrl(port: Int): String {
        val safePort = port.takeIf { it in 1..65535 } ?: DEFAULT_PORT
        return "http://127.0.0.1:$safePort/"
    }
}
