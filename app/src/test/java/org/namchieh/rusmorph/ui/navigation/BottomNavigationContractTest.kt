package org.namchieh.rusmorph.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavigationContractTest {
    @Test
    fun primaryNavigationMatchesProductContractInOrder() {
        assertEquals(
            listOf("home", "learning", "dictionary", "review", "profile"),
            BottomDestination.entries.map { it.route },
        )
    }
}
