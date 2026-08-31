package org.namchieh.rusmorph.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AssetDatabaseImporterTest {
    private val directExecutor = Executor { command -> command.run() }
    private lateinit var database: RusMorphDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RusMorphDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameAssetVersionIsNotImportedTwice() = runTest {
        val importer = AssetDatabaseImporter(context, database)
        assertTrue(importer.importIfNeeded())
        assertNotNull(database.dataImportDao().metadataValue("database_asset_version"))
        assertFalse(importer.importIfNeeded())
    }

    @Test
    fun generatedAssetsImportExpectedCountsAndProvenance() = runTest {
        val manifest = context.assets.open("database/data_manifest.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val firstEntry = context.assets.open("database/lexicon.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonArray.first().asJsonObject
        }

        assertTrue(AssetDatabaseImporter(context, database).importIfNeeded())

        val counts = manifest.getAsJsonObject("counts")
        val dao = database.dataImportDao()
        assertEquals(counts["entries"].asInt, dao.lexiconEntryCount())
        assertEquals(counts["searchForms"].asInt, dao.searchFormCount())
        assertEquals(counts["partsOfSpeechRelations"].asInt, dao.partOfSpeechCount())
        assertEquals(counts["provenanceRecords"].asInt, dao.sourceCount())
        assertEquals(counts["declensionRules"].asInt, dao.declensionRuleCount())
        assertEquals(counts["knowledgeChunks"].asInt, dao.knowledgeChunkCount())
        assertEquals(counts["crossRefs"].asInt, dao.crossRefCount())

        val details = database.searchDao().observeEntry(firstEntry["id"].asString).first()
        assertNotNull(details)
        assertEquals(firstEntry["sourceWorkbook"].asString, details!!.sources.single().sourceWorkbook)
        assertEquals(firstEntry["sourceSheet"].asString, details.sources.single().sourceSheet)
        assertEquals(firstEntry["sourceRow"].asInt, details.sources.single().sourceRow)
    }

    @Test
    fun injectedFailureRollsBackReplaceAllTransaction() = runTest {
        val initialImporter = AssetDatabaseImporter(context, database)
        assertTrue(initialImporter.importIfNeeded())
        val dao = database.dataImportDao()
        val initialCount = dao.lexiconEntryCount()
        val initialVersion = dao.metadataValue("database_asset_version")
        val assets = listOf("lexicon.json", "declension_rules.json", "knowledge_chunks.json")
            .associateWith { name -> context.assets.open("database/$name").use { it.readBytes() } }
            .toMutableMap()
        assets["knowledge_chunks.json"] = assets.getValue("knowledge_chunks.json") + '\n'.code.toByte()
        val failing = AssetDatabaseImporter(
            context = context,
            database = database,
            assetReader = assets::getValue,
            failureInjector = { error("simulated import failure") },
        )

        var failedAsExpected = false
        try {
            failing.importIfNeeded()
        } catch (_: IllegalStateException) {
            failedAsExpected = true
        }
        assertTrue(failedAsExpected)
        assertEquals(initialCount, dao.lexiconEntryCount())
        assertEquals(initialVersion, dao.metadataValue("database_asset_version"))
    }

    @Test
    fun syntheticAssociationImportsOnceAndPassesForeignKeyCheck() = runTest {
        val assets = mapOf(
            "lexicon.json" to """[
                {
                  "id":"entry","lesson":1,"sequence":1,"displayForm":"писа́ть",
                  "lemma":"писа́ть","normalizedLemma":"писать","searchForms":["писать"],
                  "partsOfSpeech":["动词"],"sourceWorkbook":"test.csv","sourceSheet":"词表",
                  "sourceRow":2,"provenance":[],"relatedKnowledgeChunkIds":["iot"]
                }
            ]""".trimIndent().encodeToByteArray(),
            "declension_rules.json" to """{"rules":[]}""".encodeToByteArray(),
            "knowledge_chunks.json" to """[
                {
                  "id":"iot","title":"j 音组","category":"IOTATION","keywords":[],
                  "content":"明确说明","examples":[],"sourceDocument":"test.docx","sectionPath":[]
                }
            ]""".trimIndent().encodeToByteArray(),
        )
        val importer = AssetDatabaseImporter(context, database, assetReader = assets::getValue)

        assertTrue(importer.importIfNeeded())
        assertEquals(1, database.dataImportDao().crossRefCount())
        assertEquals(listOf("iot"), database.searchDao().observeEntry("entry").first()!!.knowledgeChunks.map { it.id })
        assertFalse(importer.importIfNeeded())
        assertEquals(1, database.dataImportDao().crossRefCount())
    }

    @Test
    fun generatedRealAssetFixturesResolveAfterImport() = runTest {
        assertTrue(AssetDatabaseImporter(context, database).importIfNeeded())
        val fixtureFile = listOf(
            File(System.getProperty("user.dir"), "build/reports/data/real_asset_test_fixtures.json"),
            File(System.getProperty("user.dir"), "../build/reports/data/real_asset_test_fixtures.json"),
        ).firstOrNull(File::isFile)
        assertNotNull("real_asset_test_fixtures.json must be generated before tests", fixtureFile)
        val samples = fixtureFile!!.reader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("samples")
        }
        listOf("multipleSearchForms", "multiplePartsOfSpeech", "accentedDisplayForm").forEach { name ->
            val sample = samples.getAsJsonObject(name)
            assertTrue("Expected a real sample for $name", sample["available"].asBoolean)
            val id = sample.getAsJsonObject("entry")["id"].asString
            assertNotNull(database.searchDao().observeEntry(id).first())
        }
        val connectors = samples.getAsJsonArray("connectorAliases")
        assertEquals(3, connectors.size())
        connectors.forEach { element ->
            val id = element.asJsonObject["id"].asString
            val details = database.searchDao().observeEntry(id).first()!!
            assertTrue(details.partsOfSpeech.any { it.partOfSpeech == "连词" })
        }
        assertTrue(samples.getAsJsonObject("phoneticAlternation")["available"].asBoolean)
        assertTrue(samples.getAsJsonObject("conjugationClass")["available"].asBoolean)
    }

    @Test
    fun realAssetsPopulateInitialBrowseAndLessonFilterWithoutDuplicates() = runTest {
        assertTrue(AssetDatabaseImporter(context, database).importIfNeeded())
        val dao = database.searchDao()
        val lexicon = context.assets.open("database/lexicon.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonArray
        }
        assertEquals(lexicon.size(), dao.entryCount())

        val initial = dao.browseEntries(null, null, 30)
        assertEquals(30, initial.size)
        assertEquals(30, initial.map { it.entry.id }.distinct().size)

        val lessonOne = dao.browseEntries(null, 1, 1000)
        assertEquals(
            lexicon.count { element ->
                val value = element.asJsonObject["lesson"]
                value != null && !value.isJsonNull && value.asInt == 1
            },
            lessonOne.size,
        )
        assertTrue(lessonOne.all { it.entry.lesson == 1 })
        assertEquals(lessonOne.size, lessonOne.map { it.entry.id }.distinct().size)

        val fixture = lexicon.map { element -> element.asJsonObject }.first { entry ->
            entry["normalizedLemma"].asString.isNotBlank() &&
                entry.has("chineseMeaning") && !entry["chineseMeaning"].isJsonNull &&
                entry["chineseMeaning"].asString.isNotBlank()
        }
        val russian = fixture["normalizedLemma"].asString
        val chinese = fixture["chineseMeaning"].asString
        assertTrue(dao.search(russian, russian, null, null, 50).isNotEmpty())
        assertTrue(dao.search(chinese, chinese, null, null, 50).isNotEmpty())
        assertTrue(
            "Expected workbook annotations to be searchable",
            dao.search("移动重音继承型", "移动重音继承型", null, null, 50).isNotEmpty(),
        )
    }
}
