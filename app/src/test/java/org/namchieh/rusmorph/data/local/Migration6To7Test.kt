package org.namchieh.rusmorph.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test fun migrationPreservesProgressAndAddsWordBookContentTables() {
        helper.createDatabase("migration-6-7", 6).apply {
            execSQL("INSERT INTO lexicon_entries(id,lesson,sequence,displayForm,lemma,normalizedLemma,sourceWorkbook,sourceSheet,sourceRow,isRecommended) VALUES ('entry',1,7,'test','test','test','original','sheet',2,0)")
            execSQL("INSERT INTO learning_progress(sourceId,courseId,lessonId,unitType,progress,status,updatedAt) VALUES ('lesson','university-russian-1','ur1-lesson-1',NULL,0.5,'IN_PROGRESS',2)")
            execSQL("INSERT INTO saved_cards VALUES ('card','entry',NULL,1,'keep note',1,'{}',1,2)")
            execSQL("INSERT INTO review_items VALUES ('review','card','DUE',123,2)")
            execSQL("INSERT INTO generic_review_items VALUES ('generic','WORD','entry','ur1-lesson-1',123,4,0.5,2,80,2)")
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-6-7", 7, true, RusMorphDatabase.MIGRATION_6_7)
        db.query("SELECT COUNT(*) FROM learning_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT progress,status,courseId FROM learning_progress").use { it.moveToFirst(); assertEquals(0.5, it.getDouble(0), 0.0); assertEquals("IN_PROGRESS", it.getString(1)); assertEquals("university-russian-1", it.getString(2)) }
        db.query("SELECT isFavorite,userNote FROM saved_cards").use { it.moveToFirst(); assertEquals(1, it.getInt(0)); assertEquals("keep note", it.getString(1)) }
        db.query("SELECT dueAt FROM review_items").use { it.moveToFirst(); assertEquals(123, it.getInt(0)) }
        db.query("SELECT wordBookId,interval,mistakeCount FROM generic_review_items").use { it.moveToFirst(); assertEquals("university-russian-1", it.getString(0)); assertEquals(4, it.getInt(1)); assertEquals(2, it.getInt(2)) }
        db.query("SELECT COUNT(*) FROM word_books").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM word_book_lessons").use { it.moveToFirst(); assertEquals(18, it.getInt(0)) }
        db.query("SELECT wordBookId,lessonId,entryId,position FROM word_book_lesson_words").use { it.moveToFirst(); assertEquals("university-russian-1", it.getString(0)); assertEquals("ur1-lesson-1", it.getString(1)); assertEquals("entry", it.getString(2)); assertEquals(7, it.getInt(3)) }
        listOf(
            "word_book_dialogues", "word_book_dialogue_lines", "word_book_texts", "word_book_text_paragraphs",
        ).forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
        db.close()
    }
}
