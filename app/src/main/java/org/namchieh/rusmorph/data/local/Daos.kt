package org.namchieh.rusmorph.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DataImportDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key LIMIT 1")
    suspend fun metadataValue(key: String): String?

    @Upsert
    suspend fun upsertMetadata(metadata: AppMetadataEntity)

    @Query("SELECT COUNT(*) FROM lexicon_entries")
    suspend fun lexiconEntryCount(): Int

    @Query("SELECT COUNT(*) FROM entry_search_forms")
    suspend fun searchFormCount(): Int

    @Query("SELECT COUNT(*) FROM entry_parts_of_speech")
    suspend fun partOfSpeechCount(): Int

    @Query("SELECT COUNT(*) FROM entry_annotations")
    suspend fun annotationCount(): Int

    @Query("SELECT COUNT(*) FROM entry_sources")
    suspend fun sourceCount(): Int

    @Query("SELECT COUNT(*) FROM declension_rules")
    suspend fun declensionRuleCount(): Int

    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun knowledgeChunkCount(): Int

    @Query("SELECT COUNT(*) FROM entry_knowledge_cross_ref")
    suspend fun crossRefCount(): Int

    @Query("SELECT COUNT(*) FROM grammar_points")
    suspend fun grammarPointCount(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE sourceType = 'TEM4_REAL'")
    suspend fun realQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM grammar_question_cross_ref WHERE relationSource = 'SOURCE_SPREADSHEET'")
    suspend fun sourceGrammarQuestionLinkCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLexiconEntries(items: List<LexiconEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchForms(items: List<EntrySearchFormEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartsOfSpeech(items: List<EntryPartOfSpeechEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(items: List<EntryAnnotationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(items: List<EntrySourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeclensionRules(items: List<DeclensionRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunks(items: List<KnowledgeChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryKnowledgeCrossRefs(items: List<EntryKnowledgeCrossRef>)

    @Upsert
    suspend fun upsertGrammarPoints(items: List<GrammarPointEntity>)

    @Upsert
    suspend fun upsertQuestions(items: List<QuestionEntity>)

    @Upsert
    suspend fun upsertGrammarQuestionLinks(items: List<GrammarQuestionCrossRefEntity>)

    @Query("DELETE FROM entry_knowledge_cross_ref")
    suspend fun clearCrossRefs()

    @Query("DELETE FROM entry_sources")
    suspend fun clearSources()

    @Query("DELETE FROM entry_parts_of_speech")
    suspend fun clearPartsOfSpeech()

    @Query("DELETE FROM entry_annotations")
    suspend fun clearAnnotations()

    @Query("DELETE FROM entry_search_forms")
    suspend fun clearSearchForms()

    @Query("DELETE FROM declension_rules")
    suspend fun clearDeclensionRules()

    @Query("DELETE FROM knowledge_chunks")
    suspend fun clearKnowledgeChunks()

    @Query("DELETE FROM lexicon_entries")
    suspend fun clearLexiconEntries()
}

@Dao
interface SearchDao {
    @Query(
        """
        SELECT e.id AS entryId, e.normalizedLemma AS normalizedLemma,
            sf.normalizedSearchForm AS normalizedSearchForm, e.chineseMeaning AS chineseMeaning
        FROM lexicon_entries e
        LEFT JOIN entry_search_forms sf ON sf.entryId = e.id
        WHERE (:lesson IS NULL OR e.lesson = :lesson)
        AND (:partOfSpeech IS NULL OR EXISTS (
            SELECT 1 FROM entry_parts_of_speech pos
            WHERE pos.entryId = e.id AND pos.partOfSpeech = :partOfSpeech
        ))
        ORDER BY e.lesson, e.sequence, e.id
        LIMIT :limit
        """,
    )
    suspend fun fuzzyCandidateRows(partOfSpeech: String?, lesson: Int?, limit: Int): List<FuzzyCandidateRow>

    @Transaction
    @Query("SELECT * FROM lexicon_entries WHERE id IN (:entryIds)")
    suspend fun entriesByIds(entryIds: List<String>): List<LexiconEntryWithDetails>

    @Transaction
    @Query(
        """
        SELECT e.* FROM lexicon_entries e
        WHERE (:lesson IS NULL OR e.lesson = :lesson)
        AND (:partOfSpeech IS NULL OR EXISTS (
            SELECT 1 FROM entry_parts_of_speech pos
            WHERE pos.entryId = e.id AND pos.partOfSpeech = :partOfSpeech
        ))
        ORDER BY
            CASE WHEN e.lesson IS NULL THEN 1 ELSE 0 END,
            e.lesson ASC,
            CASE WHEN e.sequence IS NULL THEN 1 ELSE 0 END,
            e.sequence ASC,
            e.normalizedLemma ASC,
            e.id ASC
        LIMIT :limit
        """,
    )
    suspend fun browseEntries(
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails>

    @Transaction
    @Query(
        """
        SELECT e.* FROM lexicon_entries e
        WHERE (:lesson IS NULL OR e.lesson = :lesson)
        AND (:partOfSpeech IS NULL OR EXISTS (
            SELECT 1 FROM entry_parts_of_speech pos
            WHERE pos.entryId = e.id AND pos.partOfSpeech = :partOfSpeech
        ))
        AND (:genderCount = 0 OR e.gender IN (:genders))
        AND (:declensionClassCount = 0 OR e.declensionClass IN (:declensionClasses))
        AND (:endingTypeCount = 0 OR e.endingType IN (:endingTypes))
        AND (:aspectCount = 0 OR e.aspect IN (:aspects))
        AND (:conjugationClassCount = 0 OR e.conjugationClass IN (:conjugationClasses))
        AND (:phoneticAlternationCount = 0 OR e.phoneticAlternation IN (:phoneticAlternations))
        AND (
            :hasPhoneticAlternation IS NULL
            OR (:hasPhoneticAlternation = 1 AND e.phoneticAlternation IS NOT NULL AND TRIM(e.phoneticAlternation) != '' AND e.phoneticAlternation != '8')
            OR (:hasPhoneticAlternation = 0 AND (e.phoneticAlternation IS NULL OR TRIM(e.phoneticAlternation) = '' OR e.phoneticAlternation = '8'))
        )
        AND (
            :hasPluralStressPattern IS NULL
            OR (:hasPluralStressPattern = 1 AND e.pluralStressPattern IS NOT NULL AND TRIM(e.pluralStressPattern) != '' AND e.pluralStressPattern != '8')
            OR (:hasPluralStressPattern = 0 AND (e.pluralStressPattern IS NULL OR TRIM(e.pluralStressPattern) = '' OR e.pluralStressPattern = '8'))
        )
        ORDER BY
            CASE WHEN e.lesson IS NULL THEN 1 ELSE 0 END,
            e.lesson ASC,
            CASE WHEN e.sequence IS NULL THEN 1 ELSE 0 END,
            e.sequence ASC,
            e.normalizedLemma ASC,
            e.id ASC
        LIMIT :limit
        """,
    )
    suspend fun browseEntriesByMorphology(
        partOfSpeech: String?,
        lesson: Int?,
        genders: List<String>,
        genderCount: Int,
        declensionClasses: List<String>,
        declensionClassCount: Int,
        endingTypes: List<String>,
        endingTypeCount: Int,
        aspects: List<String>,
        aspectCount: Int,
        conjugationClasses: List<String>,
        conjugationClassCount: Int,
        phoneticAlternations: List<String>,
        phoneticAlternationCount: Int,
        hasPhoneticAlternation: Boolean?,
        hasPluralStressPattern: Boolean?,
        limit: Int,
    ): List<LexiconEntryWithDetails>

    @Transaction
    @Query(
        """
        SELECT * FROM lexicon_entries
        WHERE lastViewedAt IS NOT NULL
        ORDER BY lastViewedAt DESC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun recentEntries(limit: Int): List<LexiconEntryWithDetails>

    @Query("SELECT COUNT(*) FROM lexicon_entries")
    suspend fun entryCount(): Int

    @Transaction
    @Query(
        """
        WITH candidate_ids AS (
            SELECT id AS entryId, 0 AS matchRank
            FROM lexicon_entries
            WHERE normalizedLemma = :normalizedQuery
            UNION ALL
            SELECT entryId, 1
            FROM entry_search_forms
            WHERE normalizedSearchForm = :normalizedQuery
            UNION ALL
            SELECT entryId, 2
            FROM entry_annotations
            WHERE normalizedValue = :normalizedQuery
            UNION ALL
            SELECT id, 3
            FROM lexicon_entries
            WHERE normalizedLemma LIKE :normalizedQuery || '%'
            UNION ALL
            SELECT entryId, 4
            FROM entry_search_forms
            WHERE normalizedSearchForm LIKE :normalizedQuery || '%'
            UNION ALL
            SELECT entryId, 5
            FROM entry_annotations
            WHERE normalizedValue LIKE :normalizedQuery || '%'
            UNION ALL
            SELECT id, 6
            FROM lexicon_entries
            WHERE chineseMeaning IS NOT NULL
              AND chineseMeaning LIKE '%' || :rawQuery || '%'
            UNION ALL
            SELECT entryId, 7
            FROM entry_annotations
            WHERE normalizedValue LIKE '%' || :normalizedQuery || '%'
        ),
        ranked AS (
            SELECT entryId, MIN(matchRank) AS matchRank
            FROM candidate_ids
            GROUP BY entryId
        )
        SELECT e.*
        FROM lexicon_entries e
        INNER JOIN ranked r ON r.entryId = e.id
        WHERE (:partOfSpeech IS NULL OR EXISTS (
            SELECT 1 FROM entry_parts_of_speech pos
            WHERE pos.entryId = e.id AND pos.partOfSpeech = :partOfSpeech
        ))
        AND (:lesson IS NULL OR e.lesson = :lesson)
        ORDER BY r.matchRank, e.lesson, e.sequence, e.normalizedLemma
        LIMIT :limit
        """,
    )
    suspend fun search(
        normalizedQuery: String,
        rawQuery: String,
        partOfSpeech: String?,
        lesson: Int?,
        limit: Int,
    ): List<LexiconEntryWithDetails>

    @Transaction
    @Query(
        """
        SELECT * FROM lexicon_entries
        WHERE lastViewedAt IS NOT NULL OR isRecommended = 1
        ORDER BY
            CASE WHEN lastViewedAt IS NULL THEN 1 ELSE 0 END,
            lastViewedAt DESC,
            isRecommended DESC,
            lesson,
            sequence
        LIMIT :limit
        """,
    )
    suspend fun recentOrRecommended(limit: Int): List<LexiconEntryWithDetails>

    @Transaction
    @Query("SELECT * FROM lexicon_entries WHERE id = :entryId LIMIT 1")
    fun observeEntry(entryId: String): Flow<LexiconEntryWithDetails?>

    @Query("UPDATE lexicon_entries SET lastViewedAt = :viewedAt WHERE id = :entryId")
    suspend fun markViewed(entryId: String, viewedAt: Long)

    @Query("SELECT DISTINCT partOfSpeech FROM entry_parts_of_speech ORDER BY partOfSpeech")
    fun observePartOfSpeechOptions(): Flow<List<String>>

    @Query("SELECT DISTINCT lesson FROM lexicon_entries WHERE lesson IS NOT NULL ORDER BY lesson")
    fun observeLessonOptions(): Flow<List<Int>>

    @Query("SELECT * FROM knowledge_chunks WHERE id = :chunkId LIMIT 1")
    fun observeKnowledgeChunk(chunkId: String): Flow<KnowledgeChunkEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntriesForTest(items: List<LexiconEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchFormsForTest(items: List<EntrySearchFormEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartsOfSpeechForTest(items: List<EntryPartOfSpeechEntity>)
}

data class FuzzyCandidateRow(
    val entryId: String,
    val normalizedLemma: String,
    val normalizedSearchForm: String?,
    val chineseMeaning: String?,
)

data class SavedCardWithArchives(
    @androidx.room.Embedded val card: SavedCardEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room.Junction(
            value = ArchiveCardCrossRef::class,
            parentColumn = "savedCardId",
            entityColumn = "archiveId",
        ),
    )
    val archives: List<ArchiveEntity>,
)

@Dao
interface LocalLibraryDao {
    @Upsert suspend fun upsertCard(card: SavedCardEntity)
    @Upsert suspend fun upsertCards(cards: List<SavedCardEntity>)
    @Upsert suspend fun upsertDeck(deck: SavedDeckEntity)
    @Transaction
    suspend fun saveDeckWithCards(deck: SavedDeckEntity, cards: List<SavedCardEntity>) {
        upsertDeck(deck)
        upsertCards(cards)
    }
    @Upsert suspend fun upsertArchive(archive: ArchiveEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCardToArchive(crossRef: ArchiveCardCrossRef)

    @Transaction
    @Query("SELECT * FROM saved_cards WHERE id = :cardId LIMIT 1")
    fun observeCard(cardId: String): Flow<SavedCardWithArchives?>

    @Transaction
    @Query("SELECT * FROM saved_cards WHERE entryId = :entryId ORDER BY updatedAt DESC")
    fun observeCardsForEntry(entryId: String): Flow<List<SavedCardWithArchives>>

    @Query("SELECT * FROM saved_decks ORDER BY updatedAt DESC")
    fun observeDecks(): Flow<List<SavedDeckEntity>>

    @Query("SELECT * FROM saved_cards WHERE deckId = :deckId ORDER BY createdAt, id")
    fun observeDeckCards(deckId: String): Flow<List<SavedCardEntity>>

    @Query("SELECT * FROM archives ORDER BY name")
    fun observeArchives(): Flow<List<ArchiveEntity>>

    @Query("SELECT * FROM archives WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun archiveByName(normalizedName: String): ArchiveEntity?

    @Query("SELECT COUNT(*) FROM archive_card_cross_ref WHERE archiveId = :archiveId")
    suspend fun archiveCardCount(archiveId: String): Int

    @Query("DELETE FROM archives WHERE id = :archiveId")
    suspend fun deleteArchive(archiveId: String)

    @Query("DELETE FROM saved_cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: String)
}

@Dao
interface LearningDao {
    @Upsert suspend fun upsertProgress(item: LearningProgressEntity)
    @Upsert suspend fun upsertActivity(item: LearningActivityEntity)
    @Upsert suspend fun upsertReviewItem(item: GenericReviewItemEntity)
    @Upsert suspend fun upsertReviewItems(items: List<GenericReviewItemEntity>)
    @Upsert suspend fun upsertMistake(item: MistakeItemV2Entity)
    @Upsert suspend fun upsertPronunciationSession(item: PronunciationSessionEntity)

    @Query("DELETE FROM generic_review_items WHERE id = :id OR sourceId = :id")
    suspend fun deleteReviewItem(id: String)

    @Query("SELECT * FROM generic_review_items WHERE id = :id OR sourceId = :id LIMIT 1")
    suspend fun getReviewItemById(id: String): GenericReviewItemEntity?

    @Query("SELECT * FROM generic_review_items WHERE lessonId = :lessonId")
    suspend fun getReviewItemsByLesson(lessonId: String): List<GenericReviewItemEntity>

    @Query("SELECT * FROM learning_progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<LearningProgressEntity>>

    @Query("SELECT * FROM learning_activities ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecentActivities(limit: Int): Flow<List<LearningActivityEntity>>

    @Query("SELECT * FROM generic_review_items")
    fun observeAllReviewItems(): Flow<List<GenericReviewItemEntity>>

    @Query("SELECT * FROM generic_review_items WHERE lessonId LIKE :prefix || '%'")
    fun observeAllReviewItemsByPrefix(prefix: String): Flow<List<GenericReviewItemEntity>>

    @Query("SELECT * FROM generic_review_items WHERE dueAt IS NULL OR dueAt <= :now ORDER BY COALESCE(dueAt, 0), updatedAt")
    fun observeDueReviewItems(now: Long): Flow<List<GenericReviewItemEntity>>

    @Query("SELECT * FROM generic_review_items WHERE (lessonId LIKE :prefix || '%') AND (dueAt IS NULL OR dueAt <= :now) ORDER BY COALESCE(dueAt, 0), updatedAt")
    fun observeDueReviewItemsByPrefix(prefix: String, now: Long): Flow<List<GenericReviewItemEntity>>

    @Query("SELECT ri.id AS id, sc.entryId AS sourceId, le.lesson AS lessonNumber, ri.dueAt AS dueAt FROM review_items ri JOIN saved_cards sc ON sc.id = ri.savedCardId LEFT JOIN lexicon_entries le ON le.id = sc.entryId WHERE ri.dueAt IS NULL OR ri.dueAt <= :now ORDER BY COALESCE(ri.dueAt, 0)")
    fun observeLegacyDueItems(now: Long): Flow<List<LegacyReviewItemRow>>

    @Query("SELECT COUNT(*) FROM review_items WHERE dueAt IS NULL OR dueAt <= :now")
    fun observeLegacyDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_cards WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mistake_items_v2")
    fun observeMistakeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pronunciation_sessions WHERE completedAt IS NOT NULL")
    fun observePronunciationCount(): Flow<Int>
}

data class LegacyReviewItemRow(val id: String, val sourceId: String, val lessonNumber: Int?, val dueAt: Long?)

data class GrammarPointStatsRow(
    val pointId: String,
    val realQuestionCount: Int,
    val completedQuestionCount: Int,
)

data class GrammarAttemptEvidenceRow(
    val sourceType: String,
    val correct: Boolean,
    val createdAt: Long,
)

@Dao
interface GrammarExamDao {
    @Query("SELECT * FROM grammar_points ORDER BY sortOrder, pointId")
    fun observeAllGrammarPoints(): Flow<List<GrammarPointEntity>>

    @Query("SELECT * FROM grammar_points ORDER BY sortOrder, pointId")
    suspend fun getAllGrammarPoints(): List<GrammarPointEntity>

    @Query("SELECT * FROM grammar_points WHERE pointId = :pointId LIMIT 1")
    fun observeGrammarPoint(pointId: String): Flow<GrammarPointEntity?>

    @Query("SELECT * FROM grammar_points WHERE pointId = :pointId LIMIT 1")
    suspend fun getGrammarPoint(pointId: String): GrammarPointEntity?

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN grammar_question_cross_ref link ON link.questionId = q.questionId
        WHERE link.pointId = :pointId
        ORDER BY CASE WHEN link.verified = 1 AND link.role = 'PRIMARY' THEN 0
                      WHEN link.role = 'PRIMARY' THEN 1
                      WHEN link.role = 'RELATED' THEN 2 ELSE 3 END,
                 q.examYear, q.sourceQuestionId, q.questionId
        """,
    )
    fun observeQuestionsForGrammarPoint(pointId: String): Flow<List<QuestionEntity>>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN grammar_question_cross_ref link ON link.questionId = q.questionId
        WHERE link.pointId = :pointId
        ORDER BY CASE WHEN link.verified = 1 AND link.role = 'PRIMARY' THEN 0
                      WHEN link.role = 'PRIMARY' THEN 1
                      WHEN link.role = 'RELATED' THEN 2 ELSE 3 END,
                 q.examYear, q.sourceQuestionId, q.questionId
        """,
    )
    suspend fun getQuestionsForGrammarPoint(pointId: String): List<QuestionEntity>

    @Query(
        """
        SELECT gp.* FROM grammar_points gp
        INNER JOIN grammar_question_cross_ref link ON link.pointId = gp.pointId
        WHERE link.questionId = :questionId
        ORDER BY CASE WHEN link.verified = 1 AND link.role = 'PRIMARY' THEN 0
                      WHEN link.role = 'PRIMARY' THEN 1
                      WHEN link.role = 'RELATED' THEN 2 ELSE 3 END,
                 gp.sortOrder, gp.pointId
        """,
    )
    suspend fun getGrammarPointsForQuestion(questionId: String): List<GrammarPointEntity>

    @Query("SELECT * FROM grammar_question_cross_ref WHERE questionId = :questionId")
    suspend fun getLinksForQuestion(questionId: String): List<GrammarQuestionCrossRefEntity>

    @Query("SELECT * FROM questions WHERE sourceType = 'TEM4_REAL' ORDER BY examYear, sourceQuestionId, questionId")
    fun observeTem4Questions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE sourceType = 'TEM4_REAL' ORDER BY examYear, sourceQuestionId, questionId")
    suspend fun getTem4Questions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE questionId = :questionId LIMIT 1")
    fun observeQuestion(questionId: String): Flow<QuestionEntity?>

    @Query("SELECT * FROM questions WHERE questionId = :questionId LIMIT 1")
    suspend fun getQuestion(questionId: String): QuestionEntity?

    @Query("SELECT * FROM question_attempts WHERE questionId = :questionId ORDER BY createdAt DESC")
    fun observeAttempts(questionId: String): Flow<List<QuestionAttemptEntity>>

    @Query("SELECT * FROM question_attempts WHERE questionId = :questionId ORDER BY createdAt DESC")
    suspend fun getAttempts(questionId: String): List<QuestionAttemptEntity>

    @Query(
        """
        SELECT q.sourceType AS sourceType, attempt.correct AS correct, attempt.createdAt AS createdAt
        FROM question_attempts attempt
        INNER JOIN questions q ON q.questionId = attempt.questionId
        INNER JOIN grammar_question_cross_ref link ON link.questionId = q.questionId
        WHERE link.pointId = :pointId
        ORDER BY attempt.createdAt
        """,
    )
    suspend fun getAttemptEvidenceForGrammarPoint(pointId: String): List<GrammarAttemptEvidenceRow>

    @Query(
        """
        SELECT DISTINCT q.* FROM questions q
        INNER JOIN question_attempts attempt ON attempt.questionId = q.questionId
        WHERE attempt.correct = 0
        ORDER BY attempt.createdAt DESC
        """,
    )
    fun observeWrongQuestions(): Flow<List<QuestionEntity>>

    @Query(
        """
        SELECT gp.pointId AS pointId,
               COUNT(DISTINCT CASE WHEN q.sourceType = 'TEM4_REAL' THEN q.questionId END) AS realQuestionCount,
               COUNT(DISTINCT CASE WHEN q.sourceType = 'TEM4_REAL' AND attempt.attemptId IS NOT NULL THEN q.questionId END) AS completedQuestionCount
        FROM grammar_points gp
        LEFT JOIN grammar_question_cross_ref link ON link.pointId = gp.pointId
        LEFT JOIN questions q ON q.questionId = link.questionId
        LEFT JOIN question_attempts attempt ON attempt.questionId = q.questionId
        GROUP BY gp.pointId
        """,
    )
    fun observeGrammarPointStats(): Flow<List<GrammarPointStatsRow>>

    @Query("SELECT * FROM grammar_mastery WHERE pointId = :pointId LIMIT 1")
    fun observeGrammarMastery(pointId: String): Flow<GrammarMasteryEntity?>

    @Query("SELECT * FROM grammar_mastery")
    fun observeAllGrammarMastery(): Flow<List<GrammarMasteryEntity>>

    @Upsert
    suspend fun upsertQuestion(question: QuestionEntity)

    @Upsert
    suspend fun upsertGrammarQuestionLink(link: GrammarQuestionCrossRefEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(attempt: QuestionAttemptEntity)

    @Upsert
    suspend fun upsertMastery(mastery: GrammarMasteryEntity)

    @Upsert
    suspend fun upsertLineage(lineage: QuestionLineageEntity)

    @Query("SELECT * FROM question_lineage WHERE questionId = :questionId LIMIT 1")
    suspend fun getLineage(questionId: String): QuestionLineageEntity?
}
