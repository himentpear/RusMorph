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
        GrammarPointEntity::class,
        QuestionEntity::class,
        GrammarQuestionCrossRefEntity::class,
        QuestionAttemptEntity::class,
        GrammarMasteryEntity::class,
        QuestionLineageEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class RusMorphDatabase : RoomDatabase() {
    abstract fun dataImportDao(): DataImportDao
    abstract fun searchDao(): SearchDao
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun learningDao(): LearningDao
    abstract fun grammarExamDao(): GrammarExamDao

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
                db.execSQL("CREATE TABLE IF NOT EXISTS `grammar_points` (`pointId` TEXT NOT NULL, `titleZh` TEXT NOT NULL, `titleRu` TEXT NOT NULL, `explanation` TEXT NOT NULL, `exampleRu` TEXT, `exampleZh` TEXT, `parentPointId` TEXT, `category` TEXT, `sortOrder` INTEGER NOT NULL, `contentVersion` INTEGER NOT NULL, PRIMARY KEY(`pointId`), FOREIGN KEY(`parentPointId`) REFERENCES `grammar_points`(`pointId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_points_parentPointId` ON `grammar_points` (`parentPointId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_points_category` ON `grammar_points` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_points_sortOrder` ON `grammar_points` (`sortOrder`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `questions` (`questionId` TEXT NOT NULL, `sourceQuestionId` INTEGER, `sourceType` TEXT NOT NULL, `examYear` INTEGER, `examYearLabel` TEXT, `stem` TEXT NOT NULL, `optionA` TEXT NOT NULL, `optionB` TEXT NOT NULL, `optionC` TEXT NOT NULL, `optionD` TEXT NOT NULL, `answer` TEXT NOT NULL, `analysis` TEXT, `difficulty` REAL, `createdAt` INTEGER, PRIMARY KEY(`questionId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_sourceType` ON `questions` (`sourceType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_examYear` ON `questions` (`examYear`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_sourceQuestionId` ON `questions` (`sourceQuestionId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `grammar_question_cross_ref` (`questionId` TEXT NOT NULL, `pointId` TEXT NOT NULL, `role` TEXT NOT NULL, `weight` REAL NOT NULL, `confidence` REAL NOT NULL, `relationSource` TEXT NOT NULL, `verified` INTEGER NOT NULL, PRIMARY KEY(`questionId`, `pointId`), FOREIGN KEY(`questionId`) REFERENCES `questions`(`questionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`pointId`) REFERENCES `grammar_points`(`pointId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_question_cross_ref_pointId` ON `grammar_question_cross_ref` (`pointId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_question_cross_ref_questionId` ON `grammar_question_cross_ref` (`questionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_question_cross_ref_role` ON `grammar_question_cross_ref` (`role`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `question_attempts` (`attemptId` TEXT NOT NULL, `questionId` TEXT NOT NULL, `selectedAnswer` TEXT NOT NULL, `correct` INTEGER NOT NULL, `mode` TEXT NOT NULL, `durationMs` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`attemptId`), FOREIGN KEY(`questionId`) REFERENCES `questions`(`questionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_attempts_questionId` ON `question_attempts` (`questionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_attempts_mode` ON `question_attempts` (`mode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_attempts_createdAt` ON `question_attempts` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_attempts_correct` ON `question_attempts` (`correct`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `grammar_mastery` (`pointId` TEXT NOT NULL, `mastery` REAL NOT NULL, `realQuestionAttempts` INTEGER NOT NULL, `realQuestionCorrect` INTEGER NOT NULL, `aiQuestionAttempts` INTEGER NOT NULL, `aiQuestionCorrect` INTEGER NOT NULL, `lastReviewedAt` INTEGER, PRIMARY KEY(`pointId`), FOREIGN KEY(`pointId`) REFERENCES `grammar_points`(`pointId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `question_lineage` (`questionId` TEXT NOT NULL, `derivedFromQuestionId` TEXT, `targetPointId` TEXT, `generationId` TEXT, `modelMetadata` TEXT, PRIMARY KEY(`questionId`), FOREIGN KEY(`questionId`) REFERENCES `questions`(`questionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`derivedFromQuestionId`) REFERENCES `questions`(`questionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`targetPointId`) REFERENCES `grammar_points`(`pointId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_lineage_derivedFromQuestionId` ON `question_lineage` (`derivedFromQuestionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_lineage_targetPointId` ON `question_lineage` (`targetPointId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_lineage_generationId` ON `question_lineage` (`generationId`)")
            }
        }
    }
}
