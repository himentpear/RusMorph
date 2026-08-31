package org.namchieh.rusmorph.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.data.local.*
import org.namchieh.rusmorph.ui.screen.search.SearchContent
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class SearchBrowseComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleResultUsesReadableDictionaryRow() {
        compose.setContent {
            RusMorphTheme { SearchContent(SearchUiState(browseEntries = listOf(entry("автомоби́ль")), databaseEntryCount = 947), {}, {}) }
        }
        compose.onNodeWithText("автомоби́ль").assertIsDisplayed()
        compose.onNodeWithText("示例释义").assertIsDisplayed()
    }

    @Test fun multipleResultsAreVisibleInTheDictionaryList() {
        compose.setContent {
            RusMorphTheme { SearchContent(SearchUiState(browseEntries = listOf(entry("друг", "1"), entry("мочь", "2")), databaseEntryCount = 947), {}, {}) }
        }
        compose.onNodeWithText("друг").assertIsDisplayed()
        compose.onNodeWithText("мочь").assertIsDisplayed()
    }

    private fun entry(word: String, id: String = "1"): LexiconEntryWithDetails {
        val entity = LexiconEntryEntity(id, 1, 1, word, word, word, "示例释义", null, null, null, null, null, null, null, "test", "test", 2)
        return LexiconEntryWithDetails(entity, listOf(EntrySearchFormEntity(id, word)), listOf(EntryPartOfSpeechEntity(id, "名词")), emptyList(), emptyList())
    }
}
