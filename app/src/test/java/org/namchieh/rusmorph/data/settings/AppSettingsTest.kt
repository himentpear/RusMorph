package org.namchieh.rusmorph.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun deckLimitDefaultsToFiftyAndAcceptsConfiguredOptions() {
        assertEquals(50, AppSettings.DEFAULT_DECK_LIMIT)
        assertEquals(100, AppSettings.sanitizeDeckLimit(100))
        assertEquals(150, AppSettings.sanitizeDeckLimit(150))
        assertEquals(200, AppSettings.sanitizeDeckLimit(200))
        assertEquals(50, AppSettings.sanitizeDeckLimit(999))
    }
}
