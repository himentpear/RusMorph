package org.namchieh.rusmorph.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RusMorphDatabase::class.java,
    )

    @Test
    fun migrate3To4CreatesMorphologyIndexes() {
        helper.createDatabase("migration-3-4", 3).close()

        val migrated = helper.runMigrationsAndValidate(
            "migration-3-4",
            4,
            true,
            RusMorphDatabase.MIGRATION_3_4,
        )
        val names = buildSet {
            migrated.query("PRAGMA index_list(`lexicon_entries`)").use { cursor ->
                val nameColumn = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        assertTrue("gender index missing", "index_lexicon_entries_gender" in names)
        assertTrue(
            "composite morphology index missing",
            "index_lexicon_entries_gender_phoneticAlternation_lesson" in names,
        )
        migrated.close()
    }
}
