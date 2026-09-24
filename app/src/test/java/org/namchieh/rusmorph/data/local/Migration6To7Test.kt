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

    @Test
    fun migrationPreservesLearningDataAndAddsGrammarQuestionGraph() {
        helper.createDatabase("migration-6-7", 6).apply {
            execSQL("INSERT INTO learning_progress(sourceId,courseId,lessonId,unitType,progress,status,updatedAt) VALUES ('lesson','course','lesson',NULL,0.5,'IN_PROGRESS',2)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-6-7", 7, true, RusMorphDatabase.MIGRATION_6_7)
        db.query("SELECT COUNT(*) FROM learning_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        listOf(
            "grammar_points", "questions", "grammar_question_cross_ref",
            "question_attempts", "grammar_mastery", "question_lineage",
        ).forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(false, it.moveToFirst()) }
        db.close()
    }
}
