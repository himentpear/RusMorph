package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LexiconEntryEntity::class,
        EntrySearchFormEntity::class,
        EntryPartOfSpeechEntity::class,
        EntryAnnotationEntity::class,
        DeclensionRuleEntity::class,
        KnowledgeChunkEntity::class,
        EntryKnowledgeCrossRef::class,
        EntrySourceEntity::class,
        AppMetadataEntity::class,
        SavedCardEntity::class,
        SavedDeckEntity::class,
        ArchiveEntity::class,
        ArchiveCardCrossRef::class,
        ReviewItemEntity::class,
        ReviewAttemptEntity::class,
        WrongAnswerEntity::class,
        LearningProgressEntity::class,
        LearningActivityEntity::class,
        GenericReviewItemEntity::class,
        MistakeItemV2Entity::class,
        PronunciationSessionEntity::class,
        WordBookEntity::class,
        WordBookLessonEntity::class,
        WordBookLessonWordEntity::class,
        WordBookDialogueEntity::class,
        WordBookDialogueLineEntity::class,
        WordBookTextEntity::class,
        WordBookTextParagraphEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class RusMorphDatabase : RoomDatabase() {
    abstract fun dataImportDao(): DataImportDao
    abstract fun searchDao(): SearchDao
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun learningDao(): LearningDao
    abstract fun wordBookDao(): WordBookDao

    companion object {
        fun create(context: Context): RusMorphDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RusMorphDatabase::class.java,
                "rusmorph.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_decks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `commandText` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_decks_updatedAt` ON `saved_decks` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_cards` (`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `deckId` TEXT, `isFavorite` INTEGER NOT NULL, `userNote` TEXT, `generationVersion` INTEGER NOT NULL, `generatedSnapshotJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`deckId`) REFERENCES `saved_decks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_cards_entryId` ON `saved_cards` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_cards_deckId` ON `saved_cards` (`deckId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_cards_isFavorite` ON `saved_cards` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_cards_updatedAt` ON `saved_cards` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `archives` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_archives_normalizedName` ON `archives` (`normalizedName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `archive_card_cross_ref` (`archiveId` TEXT NOT NULL, `savedCardId` TEXT NOT NULL, PRIMARY KEY(`archiveId`, `savedCardId`), FOREIGN KEY(`archiveId`) REFERENCES `archives`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`savedCardId`) REFERENCES `saved_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_archive_card_cross_ref_savedCardId` ON `archive_card_cross_ref` (`savedCardId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `review_items` (`id` TEXT NOT NULL, `savedCardId` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `dueAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`savedCardId`) REFERENCES `saved_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_review_items_savedCardId` ON `review_items` (`savedCardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_items_reviewStatus` ON `review_items` (`reviewStatus`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `review_attempts` (`id` TEXT NOT NULL, `reviewItemId` TEXT NOT NULL, `wasCorrect` INTEGER NOT NULL, `attemptedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reviewItemId`) REFERENCES `review_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_attempts_reviewItemId` ON `review_attempts` (`reviewItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_attempts_attemptedAt` ON `review_attempts` (`attemptedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `wrong_answers` (`id` TEXT NOT NULL, `reviewAttemptId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `submittedAnswer` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reviewAttemptId`) REFERENCES `review_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wrong_answers_reviewAttemptId` ON `wrong_answers` (`reviewAttemptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wrong_answers_entryId` ON `wrong_answers` (`entryId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_gender` ON `lexicon_entries` (`gender`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_declensionClass` ON `lexicon_entries` (`declensionClass`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_endingType` ON `lexicon_entries` (`endingType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_pluralStressPattern` ON `lexicon_entries` (`pluralStressPattern`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_aspect` ON `lexicon_entries` (`aspect`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_gender_phoneticAlternation_lesson` ON `lexicon_entries` (`gender`, `phoneticAlternation`, `lesson`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_parts_of_speech_partOfSpeech_entryId` ON `entry_parts_of_speech` (`partOfSpeech`, `entryId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `entry_annotations` (`entryId` TEXT NOT NULL, `fieldName` TEXT NOT NULL, `value` TEXT NOT NULL, `normalizedValue` TEXT NOT NULL, PRIMARY KEY(`entryId`, `fieldName`, `normalizedValue`), FOREIGN KEY(`entryId`) REFERENCES `lexicon_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_annotations_entryId` ON `entry_annotations` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_annotations_normalizedValue` ON `entry_annotations` (`normalizedValue`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_annotations_normalizedValue_entryId` ON `entry_annotations` (`normalizedValue`, `entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_annotations_fieldName_normalizedValue_entryId` ON `entry_annotations` (`fieldName`, `normalizedValue`, `entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_lesson_normalizedLemma` ON `lexicon_entries` (`lesson`, `normalizedLemma`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lexicon_entries_lesson_sequence` ON `lexicon_entries` (`lesson`, `sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_search_forms_normalizedSearchForm_entryId` ON `entry_search_forms` (`normalizedSearchForm`, `entryId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `learning_progress` (`sourceId` TEXT NOT NULL, `courseId` TEXT, `lessonId` TEXT, `unitType` TEXT, `progress` REAL NOT NULL, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_progress_courseId` ON `learning_progress` (`courseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_progress_lessonId` ON `learning_progress` (`lessonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_progress_updatedAt` ON `learning_progress` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `learning_activities` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceId` TEXT, `courseId` TEXT, `lessonId` TEXT, `occurredAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_activities_occurredAt` ON `learning_activities` (`occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_activities_type` ON `learning_activities` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_activities_lessonId` ON `learning_activities` (`lessonId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `generic_review_items` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `lessonId` TEXT, `dueAt` INTEGER, `interval` INTEGER, `difficulty` REAL, `mistakeCount` INTEGER NOT NULL, `lastResult` REAL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generic_review_items_type` ON `generic_review_items` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generic_review_items_sourceId` ON `generic_review_items` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generic_review_items_lessonId` ON `generic_review_items` (`lessonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generic_review_items_dueAt` ON `generic_review_items` (`dueAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `mistake_items_v2` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `lessonId` TEXT, `count` INTEGER NOT NULL, `lastOccurredAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_items_v2_type` ON `mistake_items_v2` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_items_v2_sourceId` ON `mistake_items_v2` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_items_v2_lessonId` ON `mistake_items_v2` (`lessonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_items_v2_lastOccurredAt` ON `mistake_items_v2` (`lastOccurredAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pronunciation_sessions` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceId` TEXT, `courseId` TEXT, `lessonId` TEXT, `targetText` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER, `intelligibilityScore` REAL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_sessions_type` ON `pronunciation_sessions` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_sessions_sourceId` ON `pronunciation_sessions` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_sessions_lessonId` ON `pronunciation_sessions` (`lessonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pronunciation_sessions_startedAt` ON `pronunciation_sessions` (`startedAt`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE generic_review_items ADD COLUMN wordBookId TEXT")
                db.execSQL("UPDATE generic_review_items SET wordBookId = 'university-russian-1' WHERE lessonId LIKE 'ur1-lesson-%'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `description` TEXT NOT NULL, `coverUri` TEXT, `sourceType` TEXT NOT NULL, `version` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_books_sourceType` ON `word_books` (`sourceType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_books_updatedAt` ON `word_books` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_lessons` (`id` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `number` INTEGER NOT NULL, `titleRu` TEXT, `titleZh` TEXT, PRIMARY KEY(`wordBookId`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_lessons_wordBookId` ON `word_book_lessons` (`wordBookId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_book_lessons_wordBookId_number` ON `word_book_lessons` (`wordBookId`, `number`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_lesson_words` (`wordBookId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `position` INTEGER NOT NULL, `isKey` INTEGER NOT NULL, PRIMARY KEY(`wordBookId`, `lessonId`, `entryId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_lesson_words_lessonId` ON `word_book_lesson_words` (`lessonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_lesson_words_entryId` ON `word_book_lesson_words` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_lesson_words_wordBookId_lessonId_position` ON `word_book_lesson_words` (`wordBookId`, `lessonId`, `position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_dialogues` (`id` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`wordBookId`, `lessonId`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_dialogues_wordBookId` ON `word_book_dialogues` (`wordBookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_dialogues_lessonId` ON `word_book_dialogues` (`lessonId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_dialogue_lines` (`id` TEXT NOT NULL, `dialogueId` TEXT NOT NULL, `speaker` TEXT, `text` TEXT NOT NULL, `translation` TEXT, `audio` TEXT, `position` INTEGER NOT NULL, `wordBookId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, PRIMARY KEY(`wordBookId`, `lessonId`, `dialogueId`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_dialogue_lines_dialogueId` ON `word_book_dialogue_lines` (`dialogueId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_texts` (`id` TEXT NOT NULL, `wordBookId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `title` TEXT NOT NULL, `translationTitle` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`wordBookId`, `lessonId`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_texts_wordBookId` ON `word_book_texts` (`wordBookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_texts_lessonId` ON `word_book_texts` (`lessonId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `word_book_text_paragraphs` (`id` TEXT NOT NULL, `textId` TEXT NOT NULL, `text` TEXT NOT NULL, `translation` TEXT, `audio` TEXT, `position` INTEGER NOT NULL, `wordBookId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, PRIMARY KEY(`wordBookId`, `lessonId`, `textId`, `id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_book_text_paragraphs_textId` ON `word_book_text_paragraphs` (`textId`)")
                // Version zero marks a migrated shell; initialization installs the verified asset
                // snapshot transactionally before the learning UI is made available.
                db.execSQL("INSERT INTO word_books VALUES ('university-russian-1', '大学俄语 1', '', '', 'cover_university_russian_1', 'BUILT_IN', 0, 0)")
                for (number in 1..18) {
                    db.execSQL("INSERT INTO word_book_lessons(id,wordBookId,number,titleRu,titleZh) VALUES (?, 'university-russian-1', ?, NULL, NULL)", arrayOf("ur1-lesson-$number", number))
                }
                db.execSQL("INSERT INTO word_book_lesson_words(wordBookId,lessonId,entryId,position,isKey) SELECT 'university-russian-1', 'ur1-lesson-' || lesson, id, COALESCE(sequence, 0), 0 FROM lexicon_entries WHERE lesson BETWEEN 1 AND 18")
            }
        }
    }
}
