package org.namchieh.rusmorph.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Migration8To9Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test fun preserves_existing_reading_as_sentence_content() {
        helper.createDatabase("migration-8-9", 8).apply {
            execSQL("INSERT INTO textbooks VALUES ('book','大学俄语 1','ru','1',1)")
            execSQL("INSERT INTO textbook_lessons VALUES ('lesson','book',1,'Урок 1')")
            execSQL("INSERT INTO reading_sections VALUES ('block','lesson',1,'READING',NULL)")
            execSQL("INSERT INTO reading_paragraphs VALUES ('sentence','block',1,'Э́то ма́ма.')")
            close()
        }
        helper.runMigrationsAndValidate("migration-8-9", 9, true, RusMorphDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT id,lessonId,blockId,text,sourceText FROM lesson_sentences").use { cursor ->
                cursor.moveToFirst()
                assertEquals("sentence", cursor.getString(0))
                assertEquals("lesson", cursor.getString(1))
                assertEquals("block", cursor.getString(2))
                assertEquals("Э́то ма́ма.", cursor.getString(3))
                assertEquals("Э́то ма́ма.", cursor.getString(4))
            }
        }
    }
}
