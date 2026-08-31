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
class Migration5To6Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test fun migrationPreservesLegacyReviewAndAddsLearningTables() {
        helper.createDatabase("migration-5-6", 5).apply {
            execSQL("INSERT INTO saved_decks(id,title,commandText,createdAt,updatedAt) VALUES ('deck','旧卡组',NULL,1,1)")
            execSQL("INSERT INTO saved_cards(id,entryId,deckId,isFavorite,userNote,generationVersion,generatedSnapshotJson,createdAt,updatedAt) VALUES ('card','entry','deck',1,NULL,1,'{}',1,1)")
            execSQL("INSERT INTO review_items(id,savedCardId,reviewStatus,dueAt,updatedAt) VALUES ('review','card','DUE',1,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-5-6", 6, true, RusMorphDatabase.MIGRATION_5_6)
        db.query("SELECT COUNT(*) FROM review_items").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO learning_progress(sourceId,courseId,lessonId,unitType,progress,status,updatedAt) VALUES ('lesson','course','lesson',NULL,0.5,'IN_PROGRESS',2)")
        db.query("SELECT COUNT(*) FROM learning_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.close()
    }
}
