package org.namchieh.rusmorph.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "lexicon_entries",
    indices = [
        Index("normalizedLemma"),
        Index("chineseMeaning"),
        Index("lesson"),
        Index("phoneticAlternation"),
        Index("conjugationClass"),
        Index("gender"),
        Index("declensionClass"),
        Index("endingType"),
        Index("pluralStressPattern"),
        Index("aspect"),
        Index(value = ["gender", "phoneticAlternation", "lesson"]),
        Index(value = ["lesson", "normalizedLemma"]),
        Index(value = ["lesson", "sequence"]),
    ],
)
data class LexiconEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val lesson: Int?,
    val sequence: Int?,
    val displayForm: String,
    val lemma: String,
    val normalizedLemma: String,
    val chineseMeaning: String?,
    val gender: String?,
    val declensionClass: String?,
    val endingType: String?,
    val pluralStressPattern: String?,
    val aspect: String?,
    val conjugationClass: String?,
    val phoneticAlternation: String?,
    val sourceWorkbook: String,
    val sourceSheet: String,
    val sourceRow: Int,
    val lastViewedAt: Long? = null,
    val isRecommended: Boolean = false,
)

@Entity(
    tableName = "entry_search_forms",
    primaryKeys = ["entryId", "normalizedSearchForm"],
    foreignKeys = [
        ForeignKey(
            entity = LexiconEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("normalizedSearchForm"),
        Index("entryId"),
        Index(value = ["normalizedSearchForm", "entryId"]),
    ],
)
data class EntrySearchFormEntity(
    val entryId: String,
    val normalizedSearchForm: String,
)

@Entity(
    tableName = "entry_parts_of_speech",
    primaryKeys = ["entryId", "partOfSpeech"],
    foreignKeys = [
        ForeignKey(
            entity = LexiconEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("partOfSpeech"),
        Index("entryId"),
        Index(value = ["partOfSpeech", "entryId"]),
    ],
)
data class EntryPartOfSpeechEntity(
    val entryId: String,
    val partOfSpeech: String,
)

@Entity(
    tableName = "entry_annotations",
    primaryKeys = ["entryId", "fieldName", "normalizedValue"],
    foreignKeys = [
        ForeignKey(
            entity = LexiconEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("entryId"),
        Index("normalizedValue"),
        Index(value = ["normalizedValue", "entryId"]),
        Index(value = ["fieldName", "normalizedValue", "entryId"]),
    ],
)
data class EntryAnnotationEntity(
    val entryId: String,
    val fieldName: String,
    val value: String,
    val normalizedValue: String,
)

@Entity(
    tableName = "declension_rules",
    indices = [Index("category"), Index("gender"), Index("endingType")],
)
data class DeclensionRuleEntity(
    @androidx.room.PrimaryKey val id: String,
    val category: String,
    val gender: String?,
    val endingType: String?,
    val number: String?,
    val caseName: String,
    val resultEnding: String,
    val description: String,
    val sourceWorkbook: String?,
    val sourceSheet: String,
    val sourceCellRange: String,
)

@Entity(tableName = "knowledge_chunks", indices = [Index("category")])
data class KnowledgeChunkEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val category: String,
    val keywordsJson: String,
    val content: String,
    val examplesJson: String,
    val sourceDocument: String,
    val sectionPathJson: String,
)

@Entity(
    tableName = "entry_knowledge_cross_ref",
    primaryKeys = ["entryId", "knowledgeChunkId"],
    foreignKeys = [
        ForeignKey(
            entity = LexiconEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KnowledgeChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgeChunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId"), Index("knowledgeChunkId")],
)
data class EntryKnowledgeCrossRef(
    val entryId: String,
    val knowledgeChunkId: String,
)

@Entity(
    tableName = "entry_sources",
    primaryKeys = ["entryId", "sourceWorkbook", "sourceSheet", "sourceRow"],
    foreignKeys = [
        ForeignKey(
            entity = LexiconEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class EntrySourceEntity(
    val entryId: String,
    val sourceWorkbook: String,
    val sourceSheet: String,
    val sourceRow: Int,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "saved_decks", indices = [Index("updatedAt")])
data class SavedDeckEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val commandText: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "saved_cards",
    indices = [Index("entryId"), Index("deckId"), Index("isFavorite"), Index("updatedAt")],
    foreignKeys = [
        ForeignKey(
            entity = SavedDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class SavedCardEntity(
    @androidx.room.PrimaryKey val id: String,
    val entryId: String,
    val deckId: String?,
    val isFavorite: Boolean,
    val userNote: String?,
    val generationVersion: Int,
    val generatedSnapshotJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "archives", indices = [Index("normalizedName", unique = true)])
data class ArchiveEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "archive_card_cross_ref",
    primaryKeys = ["archiveId", "savedCardId"],
    indices = [Index("savedCardId")],
    foreignKeys = [
        ForeignKey(entity = ArchiveEntity::class, parentColumns = ["id"], childColumns = ["archiveId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SavedCardEntity::class, parentColumns = ["id"], childColumns = ["savedCardId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ArchiveCardCrossRef(val archiveId: String, val savedCardId: String)

@Entity(
    tableName = "review_items",
    indices = [Index("savedCardId", unique = true), Index("reviewStatus")],
    foreignKeys = [ForeignKey(entity = SavedCardEntity::class, parentColumns = ["id"], childColumns = ["savedCardId"], onDelete = ForeignKey.CASCADE)],
)
data class ReviewItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val savedCardId: String,
    val reviewStatus: String,
    val dueAt: Long?,
    val updatedAt: Long,
)

@Entity(
    tableName = "review_attempts",
    indices = [Index("reviewItemId"), Index("attemptedAt")],
    foreignKeys = [ForeignKey(entity = ReviewItemEntity::class, parentColumns = ["id"], childColumns = ["reviewItemId"], onDelete = ForeignKey.CASCADE)],
)
data class ReviewAttemptEntity(
    @androidx.room.PrimaryKey val id: String,
    val reviewItemId: String,
    val wasCorrect: Boolean,
    val attemptedAt: Long,
)

@Entity(
    tableName = "wrong_answers",
    indices = [Index("reviewAttemptId"), Index("entryId")],
    foreignKeys = [ForeignKey(entity = ReviewAttemptEntity::class, parentColumns = ["id"], childColumns = ["reviewAttemptId"], onDelete = ForeignKey.CASCADE)],
)
data class WrongAnswerEntity(
    @androidx.room.PrimaryKey val id: String,
    val reviewAttemptId: String,
    val entryId: String,
    val submittedAnswer: String?,
    val createdAt: Long,
)

@Entity(tableName = "learning_progress", indices = [Index("courseId"), Index("lessonId"), Index("updatedAt")])
data class LearningProgressEntity(
    @androidx.room.PrimaryKey val sourceId: String,
    val courseId: String?,
    val lessonId: String?,
    val unitType: String?,
    val progress: Float,
    val status: String,
    val updatedAt: Long,
)

@Entity(tableName = "learning_activities", indices = [Index("occurredAt"), Index("type"), Index("lessonId")])
data class LearningActivityEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val sourceId: String?,
    val courseId: String?,
    val lessonId: String?,
    val occurredAt: Long,
    val durationSeconds: Int,
)

@Entity(tableName = "generic_review_items", indices = [Index("type"), Index("sourceId"), Index("lessonId"), Index("dueAt")])
data class GenericReviewItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val sourceId: String,
    val lessonId: String?,
    val dueAt: Long?,
    val interval: Int?,
    val difficulty: Double?,
    val mistakeCount: Int,
    val lastResult: Double?,
    val updatedAt: Long,
    val wordBookId: String? = null,
)

@Entity(tableName = "mistake_items_v2", indices = [Index("type"), Index("sourceId"), Index("lessonId"), Index("lastOccurredAt")])
data class MistakeItemV2Entity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val sourceId: String,
    val lessonId: String?,
    val count: Int,
    val lastOccurredAt: Long,
)

@Entity(tableName = "pronunciation_sessions", indices = [Index("type"), Index("sourceId"), Index("lessonId"), Index("startedAt")])
data class PronunciationSessionEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val sourceId: String?,
    val courseId: String?,
    val lessonId: String?,
    val targetText: String,
    val startedAt: Long,
    val completedAt: Long?,
    val intelligibilityScore: Double?,
)

@Entity(tableName = "word_books", indices = [Index("sourceType"), Index("updatedAt")])
data class WordBookEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val coverUri: String?,
    val sourceType: String,
    val version: Int,
    val updatedAt: Long,
)

@Entity(tableName = "word_book_lessons", primaryKeys = ["wordBookId", "id"], indices = [Index("wordBookId"), Index(value = ["wordBookId", "number"], unique = true)])
data class WordBookLessonEntity(
    val id: String,
    val wordBookId: String,
    val number: Int,
    val titleRu: String?,
    val titleZh: String?,
)

@Entity(
    tableName = "word_book_lesson_words",
    primaryKeys = ["wordBookId", "lessonId", "entryId"],
    indices = [Index("lessonId"), Index("entryId"), Index(value = ["wordBookId", "lessonId", "position"])],
)
data class WordBookLessonWordEntity(
    val wordBookId: String,
    val lessonId: String,
    val entryId: String,
    val position: Int,
    val isKey: Boolean,
)

@Entity(tableName = "word_book_dialogues", primaryKeys = ["wordBookId", "lessonId", "id"], indices = [Index("wordBookId"), Index("lessonId")])
data class WordBookDialogueEntity(
    val id: String,
    val wordBookId: String,
    val lessonId: String,
    val title: String,
    val description: String?,
    val position: Int,
)

@Entity(tableName = "word_book_dialogue_lines", primaryKeys = ["wordBookId", "lessonId", "dialogueId", "id"], indices = [Index("dialogueId")])
data class WordBookDialogueLineEntity(
    val id: String,
    val dialogueId: String,
    val speaker: String?,
    val text: String,
    val translation: String?,
    val audio: String?,
    val position: Int,
    val wordBookId: String,
    val lessonId: String,
)

@Entity(tableName = "word_book_texts", primaryKeys = ["wordBookId", "lessonId", "id"], indices = [Index("wordBookId"), Index("lessonId")])
data class WordBookTextEntity(
    val id: String,
    val wordBookId: String,
    val lessonId: String,
    val title: String,
    val translationTitle: String?,
    val position: Int,
)

@Entity(tableName = "word_book_text_paragraphs", primaryKeys = ["wordBookId", "lessonId", "textId", "id"], indices = [Index("textId")])
data class WordBookTextParagraphEntity(
    val id: String,
    val textId: String,
    val text: String,
    val translation: String?,
    val audio: String?,
    val position: Int,
    val wordBookId: String,
    val lessonId: String,
)

/** Verified textbook reading corpus. This is intentionally separate from WordBook data. */
@Entity(tableName = "textbooks")
data class TextbookEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val language: String,
    val level: String,
    val sourceVersion: Int,
)

@Entity(
    tableName = "textbook_lessons",
    primaryKeys = ["textbookId", "id"],
    indices = [Index("textbookId"), Index(value = ["textbookId", "lessonNumber"], unique = true)],
)
data class TextbookLessonEntity(
    val id: String,
    val textbookId: String,
    val lessonNumber: Int,
    val title: String,
)

@Entity(tableName = "textbook_blocks", indices = [Index("lessonId"), Index(value = ["lessonId", "order"])])
data class TextbookBlockEntity(
    @androidx.room.PrimaryKey val id: String,
    val lessonId: String,
    val order: Int,
    val type: String,
    val title: String?,
)

@Entity(tableName = "lesson_sentences", indices = [Index("lessonId"), Index("blockId"), Index(value = ["blockId", "order"])])
data class LessonSentenceEntity(
    @androidx.room.PrimaryKey val id: String,
    val lessonId: String,
    val blockId: String,
    val order: Int,
    val text: String,
    val sourceText: String,
)

@Entity(tableName = "sentence_knowledge", indices = [Index("sentenceId"), Index("type"), Index(value = ["sentenceId", "type"])])
data class SentenceKnowledgeEntity(
    @androidx.room.PrimaryKey val id: String,
    val sentenceId: String,
    val type: String,
    val text: String,
    val label: String?,
    val explanation: String?,
    val example: String?,
    val start: Int,
    val end: Int,
    val status: String?,
    val knowledgeVersion: Int,
    val generatedBy: String?,
    val reviewStatus: String?,
)

@Entity(
    tableName = "knowledge_progress",
    indices = [
        Index("status"),
        Index("lessonId"),
        Index("updatedAt"),
    ],
)
data class KnowledgeProgressEntity(
    @androidx.room.PrimaryKey val knowledgeId: String,
    val status: String,
    val seenCount: Int,
    val understoodAt: Long?,
    val practicedCount: Int,
    val masteredAt: Long?,
    val updatedAt: Long,
    val lessonId: String?,
)

