package org.namchieh.rusmorph.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.namchieh.rusmorph.data.local.AssetDatabaseImporter
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.WordBookAssetImporter
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordBookRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: RusMorphDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val direct = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
    }
    @After fun tearDown() = database.close()

    @Test fun bundled_wordbooks_are_available_offline_and_import_idempotently() = runTest {
        AssetDatabaseImporter(context, database).initialize()
        val search = SearchRepository(RoomSearchDataSource(database.searchDao()))
        val assets = AssetWordBookDataSource(context, search)
        assertEquals(setOf("university-russian-1", "university-russian-2"), assets.wordBooks().map { it.id }.toSet())
        WordBookAssetImporter(context, database).importIfNeeded()
        WordBookAssetImporter(context, database).importIfNeeded()
        val room = RoomWordBookDataSource(database.wordBookDao(), search)
        assertEquals(18, room.lessons("university-russian-1").size)
        assertEquals(12, room.lessons("university-russian-2").size)
    }

    @Test fun composite_prefers_a_newer_import_without_losing_asset_fallback() = runTest {
        AssetDatabaseImporter(context, database).initialize()
        val search = SearchRepository(RoomSearchDataSource(database.searchDao()))
        val assets = AssetWordBookDataSource(context, search)
        val room = RoomWordBookDataSource(database.wordBookDao(), search)
        val repository = CompositeWordBookRepository(assets, room)
        assertTrue(repository.lessons("university-russian-1").isNotEmpty())
    }
}
