package com.elysium.nexus.core.transport.mac

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastHostScannerTest {

    @Test
    fun `scanner excludes every local interface address`() {
        val localAddresses = setOf("192.168.1.3", "10.0.0.8", "127.0.0.1")

        assertFalse(FastHostScanner.isRemoteCandidate("192.168.1.3", localAddresses))
        assertFalse(FastHostScanner.isRemoteCandidate("10.0.0.8", localAddresses))
        assertTrue(FastHostScanner.isRemoteCandidate("192.168.1.7", localAddresses))
    }

    @Test
    fun `scanner never discovers loopback as a network Mac`() {
        assertFalse(FastHostScanner.isRemoteCandidate("127.0.0.1", emptySet()))
        assertFalse(FastHostScanner.isRemoteCandidate("localhost", emptySet()))
        assertFalse(FastHostScanner.isRemoteCandidate("::1", emptySet()))
    }
}
