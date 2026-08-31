package org.namchieh.rusmorph.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class LexiconEntryWithDetails(
    @Embedded val entry: LexiconEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val searchForms: List<EntrySearchFormEntity>,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val partsOfSpeech: List<EntryPartOfSpeechEntity>,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val sources: List<EntrySourceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryKnowledgeCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "knowledgeChunkId",
        ),
    )
    val knowledgeChunks: List<KnowledgeChunkEntity>,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val annotations: List<EntryAnnotationEntity> = emptyList(),
)
