package app.sonicsound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageServerPairingTest {
    @Before
    fun resetPairing() {
        MessageServer.disablePairing()
    }

    @Test
    fun pairingInactiveByDefault() {
        assertFalse(MessageServer.isPairingActive())
    }

    @Test
    fun enablePairing_activatesWindow() {
        MessageServer.enablePairing(60_000)
        assertTrue(MessageServer.isPairingActive())
        MessageServer.disablePairing()
        assertFalse(MessageServer.isPairingActive())
    }
}
