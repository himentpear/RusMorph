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
@Config(sdk = [34], application = android.app.Application::class)
class Migration9To10Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test
    fun migration9To10_creates_knowledge_progress_table_and_preserves_sentence_knowledge() {
        helper.createDatabase("migration-9-10", 9).apply {
            // Insert sample sentence knowledge in version 9
            execSQL(
                "INSERT INTO sentence_knowledge (id, sentenceId, type, text, label, explanation, example, start, end, status, knowledgeVersion, generatedBy, reviewStatus) " +
                    "VALUES ('k1', 's1', 'PHRASE', 'С детства', '起点', '从童年起', 'С детства...', 0, 9, 'OK', 1, 'human', 'OK')"
            )
            close()
        }

        // Run migration 9 to 10
        val db = helper.runMigrationsAndValidate("migration-9-10", 10, true, RusMorphDatabase.MIGRATION_9_10)

        // Verify existing sentence_knowledge is intact
        db.query("SELECT COUNT(*) FROM sentence_knowledge").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT id, text, label FROM sentence_knowledge WHERE id = 'k1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("k1", cursor.getString(0))
            assertEquals("С детства", cursor.getString(1))
            assertEquals("起点", cursor.getString(2))
        }

        // Verify knowledge_progress table exists and is operational
        db.execSQL(
            "INSERT INTO knowledge_progress (knowledgeId, status, seenCount, understoodAt, practicedCount, masteredAt, updatedAt, lessonId) " +
                "VALUES ('k1', 'UNDERSTOOD', 2, 1000, 0, NULL, 1000, 'ur2-lesson-1')"
        )
        db.query("SELECT COUNT(*) FROM knowledge_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT knowledgeId, status, seenCount FROM knowledge_progress WHERE knowledgeId = 'k1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("k1", cursor.getString(0))
            assertEquals("UNDERSTOOD", cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
        }

        db.close()
    }
}
