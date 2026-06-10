package io.github.sanitised.st.chat

internal fun runAlignedBridgeWrite(
    reload: () -> Unit,
    write: () -> Unit,
) {
    reload()
    write()
}
