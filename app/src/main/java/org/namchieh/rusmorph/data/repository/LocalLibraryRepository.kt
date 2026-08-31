package org.namchieh.rusmorph.data.repository

import com.google.gson.Gson
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.namchieh.rusmorph.agent.CardDeck
import org.namchieh.rusmorph.agent.WordCard
import org.namchieh.rusmorph.data.local.ArchiveCardCrossRef
import org.namchieh.rusmorph.data.local.ArchiveEntity
import org.namchieh.rusmorph.data.local.LocalLibraryDao
import org.namchieh.rusmorph.data.local.SavedCardEntity
import org.namchieh.rusmorph.data.local.SavedCardWithArchives
import org.namchieh.rusmorph.data.local.SavedDeckEntity

sealed interface ArchiveDeletionResult {
    data class ConfirmationRequired(val cardCount: Int) : ArchiveDeletionResult
    data object Deleted : ArchiveDeletionResult
    data object NotFound : ArchiveDeletionResult
}

class LocalLibraryRepository(
    private val dao: LocalLibraryDao,
    private val gson: Gson = Gson(),
) {
    fun observeCard(cardId: String): Flow<SavedCardWithArchives?> = dao.observeCard(cardId)
    fun observeCardsForEntry(entryId: String): Flow<List<SavedCardWithArchives>> = dao.observeCardsForEntry(entryId)
    fun observeDecks(): Flow<List<SavedDeckEntity>> = dao.observeDecks()
    fun observeDeckCards(deckId: String): Flow<List<SavedCardEntity>> = dao.observeDeckCards(deckId)
    fun observeArchives(): Flow<List<ArchiveEntity>> = dao.observeArchives()

    suspend fun saveCard(card: WordCard, favorite: Boolean = true, note: String? = null, deckId: String? = null): String {
        val now = System.currentTimeMillis()
        dao.upsertCard(card.toEntity(deckId, favorite, note, now))
        return card.id
    }

    suspend fun saveDeck(deck: CardDeck, commandText: String? = null) {
        val now = System.currentTimeMillis()
        dao.saveDeckWithCards(
            SavedDeckEntity(deck.id, deck.title, commandText?.trim()?.takeIf(String::isNotBlank), now, now),
            deck.cards.map { it.toEntity(deck.id, it.favorite, null, now) },
        )
    }

    suspend fun addCardToArchive(card: WordCard, archiveName: String): String {
        require(archiveName.isNotBlank()) { "Archive name must not be blank" }
        saveCard(card)
        val normalized = normalizeArchiveName(archiveName)
        val now = System.currentTimeMillis()
        val archive = dao.archiveByName(normalized) ?: ArchiveEntity(
            UUID.randomUUID().toString(), archiveName.trim(), normalized, now, now,
        ).also { dao.upsertArchive(it) }
        dao.addCardToArchive(ArchiveCardCrossRef(archive.id, card.id))
        return archive.id
    }

    suspend fun deleteArchive(archiveName: String, confirmed: Boolean): ArchiveDeletionResult {
        val archive = dao.archiveByName(normalizeArchiveName(archiveName)) ?: return ArchiveDeletionResult.NotFound
        val count = dao.archiveCardCount(archive.id)
        if (!confirmed) return ArchiveDeletionResult.ConfirmationRequired(count)
        dao.deleteArchive(archive.id)
        return ArchiveDeletionResult.Deleted
    }

    private fun WordCard.toEntity(deckId: String?, favorite: Boolean, note: String?, now: Long) = SavedCardEntity(
        id = id,
        entryId = entryId,
        deckId = deckId,
        isFavorite = favorite,
        userNote = note?.trim()?.takeIf(String::isNotBlank),
        generationVersion = 1,
        generatedSnapshotJson = gson.toJson(this),
        createdAt = now,
        updatedAt = now,
    )
}

private fun normalizeArchiveName(name: String): String = name.trim().lowercase(Locale.ROOT)
