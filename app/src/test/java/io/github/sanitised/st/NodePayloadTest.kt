package io.github.sanitised.st

import org.junit.Assert.assertEquals
import org.junit.Test

class NodePayloadTest {
    @Test
    fun bundledNodeAssetSelectionAcceptsLibnodeSoWhenNodeAssetIsMissing() {
        val selected = NodePayload.selectBundledNodeAsset(
            supportedAbis = arrayOf("arm64-v8a", "x86_64"),
            assetExists = { path -> path == "node_payload/bin/arm64-v8a/libnode.so" }
        )

        assertEquals(
            NodePayload.BundledNodeAsset(
                abi = "arm64-v8a",
                assetPath = "node_payload/bin/arm64-v8a/libnode.so"
            ),
            selected
        )
    }
}
