package org.namchieh.rusmorph.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.agent.CardDeck
import org.namchieh.rusmorph.agent.WordCard
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalLibraryRepositoryTest {
    private lateinit var context: Context
    private val databaseName = "local-library-${UUID.randomUUID()}.db"
    private var database: RusMorphDatabase? = null

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(databaseName); open() }
    @After fun tearDown() { database?.close(); context.deleteDatabase(databaseName) }

    @Test fun cardCanBeFavoriteAndBelongToMultipleArchives() = runTest {
        val repository = LocalLibraryRepository(database!!.localLibraryDao())
        val card = card()
        repository.saveCard(card)
        repository.addCardToArchive(card, "动词")
        repository.addCardToArchive(card, "易混词")
        val saved = repository.observeCard(card.id).first()!!
        assertTrue(saved.card.isFavorite)
        assertEquals(setOf("动词", "易混词"), saved.archives.map { it.name }.toSet())
    }

    @Test fun deletingArchiveRequiresConfirmationAndDoesNotDeleteCard() = runTest {
        val repository = LocalLibraryRepository(database!!.localLibraryDao())
        val card = card(); repository.addCardToArchive(card, "易混词")
        assertEquals(ArchiveDeletionResult.ConfirmationRequired(1), repository.deleteArchive("易混词", false))
        assertEquals(ArchiveDeletionResult.Deleted, repository.deleteArchive("易混词", true))
        assertTrue(repository.observeCard(card.id).first() != null)
    }

    @Test fun savedDeckSurvivesDatabaseReopen() = runTest {
        val deck = CardDeck("deck-1", "第一课动词", listOf(card()))
        LocalLibraryRepository(database!!.localLibraryDao()).saveDeck(deck, "第一课动词")
        database!!.close(); database = null; open()
        val repository = LocalLibraryRepository(database!!.localLibraryDao())
        assertEquals("deck-1", repository.observeDecks().first().single().id)
        assertEquals(listOf("card-1"), repository.observeDeckCards("deck-1").first().map { it.id })
    }

    private fun open() {
        database = Room.databaseBuilder(context, RusMorphDatabase::class.java, databaseName)
            .addMigrations(RusMorphDatabase.MIGRATION_2_3, RusMorphDatabase.MIGRATION_3_4)
            .build()
    }
    private fun card() = WordCard(id = "card-1", entryId = "entry-1", word = "сло́во", normalizedWord = "слово", meanings = listOf("词"), partsOfSpeech = listOf("名词"))
}
