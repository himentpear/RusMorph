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
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RusMorphDatabase::class.java,
    )

    @Test
    fun migrate4To5PreservesLexiconAndCreatesSearchableAnnotations() {
        helper.createDatabase("migration-4-5", 4).apply {
            execSQL(
                """
                INSERT INTO lexicon_entries (
                    id,lesson,sequence,displayForm,lemma,normalizedLemma,
                    chineseMeaning,gender,declensionClass,endingType,
                    pluralStressPattern,aspect,conjugationClass,
                    phoneticAlternation,sourceWorkbook,sourceSheet,sourceRow,
                    lastViewedAt,isRecommended
                ) VALUES (
                    'entry',1,1,'дру́г','друг','друг','朋友','阳性','2',
                    '辅音',NULL,NULL,NULL,'г-ж','test.xlsx','词表',2,NULL,0
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            "migration-4-5",
            5,
            true,
            RusMorphDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT COUNT(*) FROM lexicon_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.execSQL(
            "INSERT INTO entry_annotations(entryId,fieldName,value,normalizedValue) VALUES ('entry','语音交替','г-ж','г-ж')",
        )
        migrated.query(
            "SELECT entryId FROM entry_annotations WHERE normalizedValue = 'г-ж'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("entry", cursor.getString(0))
        }
        migrated.close()
    }
}
