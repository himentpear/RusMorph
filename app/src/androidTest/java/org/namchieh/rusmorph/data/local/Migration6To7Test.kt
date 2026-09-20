package org.namchieh.rusmorph.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Migration6To7Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test fun preserves_existing_progress_and_creates_versioned_wordbook_tables() {
        helper.createDatabase("migration-6-7", 6).apply {
            execSQL("INSERT INTO lexicon_entries(id,lesson,sequence,displayForm,lemma,normalizedLemma,sourceWorkbook,sourceSheet,sourceRow,isRecommended) VALUES ('entry',1,7,'test','test','test','source','sheet',2,0)")
            execSQL("INSERT INTO learning_progress(sourceId,courseId,lessonId,unitType,progress,status,updatedAt) VALUES ('lesson','university-russian-1','ur1-lesson-1',NULL,0.5,'IN_PROGRESS',2)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-6-7", 7, true, RusMorphDatabase.MIGRATION_6_7)
        db.query("SELECT progress,status FROM learning_progress").use { cursor ->
            cursor.moveToFirst(); assertEquals(.5, cursor.getDouble(0), 0.0); assertEquals("IN_PROGRESS", cursor.getString(1))
        }
        db.query("SELECT COUNT(*) FROM word_books").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        db.query("SELECT COUNT(*) FROM word_book_lesson_words").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        db.close()
    }
}
