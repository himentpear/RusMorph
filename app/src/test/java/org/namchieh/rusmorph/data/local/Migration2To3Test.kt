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
class Migration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RusMorphDatabase::class.java)

    @Test fun migrate2To3PreservesLexiconAndCreatesLocalLibrary() {
        helper.createDatabase("migration-2-3", 2).apply {
            execSQL("INSERT INTO lexicon_entries (id,lesson,sequence,displayForm,lemma,normalizedLemma,chineseMeaning,gender,declensionClass,endingType,pluralStressPattern,aspect,conjugationClass,phoneticAlternation,sourceWorkbook,sourceSheet,sourceRow,lastViewedAt,isRecommended) VALUES ('entry',1,1,'сло́во','слово','слово','词',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'test','test',1,NULL,0)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate("migration-2-3", 3, true, RusMorphDatabase.MIGRATION_2_3)
        migrated.query("SELECT COUNT(*) FROM lexicon_entries").use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM saved_cards").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        migrated.close()
    }
}
