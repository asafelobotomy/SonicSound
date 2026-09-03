package app.sonicsound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConstantsTest {
    @Test
    fun playAndPauseActions_areDistinctFromToggle() {
        assertNotEquals(Constants.SERVICE_PLAY, Constants.SERVICE_PLAY_PAUSE)
        assertNotEquals(Constants.SERVICE_PAUSE, Constants.SERVICE_PLAY_PAUSE)
        assertNotEquals(Constants.SERVICE_PLAY, Constants.SERVICE_PAUSE)
        assertEquals("PLAY", Constants.SERVICE_PLAY)
        assertEquals("PAUSE", Constants.SERVICE_PAUSE)
    }
}
