package org.namchieh.rusmorph

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.local.AssetDatabaseImporter
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.local.WordBookAssetImporter
import org.namchieh.rusmorph.data.local.TextbookAssetImporter
import org.namchieh.rusmorph.data.repository.RoomSearchDataSource
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.agent.AgentContextBuilder
import org.namchieh.rusmorph.data.repository.DefaultAgentRepository
import org.namchieh.rusmorph.data.diagnostics.ApiDiagnostics
import org.namchieh.rusmorph.data.repository.KnowledgeRetriever
import org.namchieh.rusmorph.data.repository.LocalLibraryRepository
import org.namchieh.rusmorph.agent.MultiAgentCoordinator
import org.namchieh.rusmorph.data.settings.AppSettings
import org.namchieh.rusmorph.data.repository.SpeechRepository
import org.namchieh.rusmorph.data.repository.CourseRepository
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.data.repository.AssetWordBookDataSource
import org.namchieh.rusmorph.data.repository.CompositeWordBookRepository
import org.namchieh.rusmorph.data.repository.RoomWordBookDataSource
import org.namchieh.rusmorph.data.repository.TextbookRepository

class RusMorphApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: RusMorphDatabase by lazy { RusMorphDatabase.create(this) }
    val dataInitializer: AssetDatabaseImporter by lazy {
        AssetDatabaseImporter(this, database)
    }
    val searchRepository: SearchRepository by lazy {
        SearchRepository(RoomSearchDataSource(database.searchDao()))
    }
    val knowledgeRetriever: KnowledgeRetriever by lazy { KnowledgeRetriever(searchRepository) }
    val agentContextBuilder: AgentContextBuilder by lazy { AgentContextBuilder() }
    val apiDiagnostics by lazy { ApiDiagnostics(this) }
    val appSettings by lazy { AppSettings(this) }
    val agentRepository by lazy {
        DefaultAgentRepository.create(
            BuildConfig.AGENT_PROXY_BASE_URL,
            apiDiagnostics,
        )
    }
    val localLibraryRepository by lazy { LocalLibraryRepository(database.localLibraryDao()) }
    val wordBookAssetImporter by lazy { WordBookAssetImporter(this, database) }
    val textbookAssetImporter by lazy { TextbookAssetImporter(this, database) }
    val textbookRepository by lazy { TextbookRepository(database.textbookDao()) }
    val wordBookRepository by lazy {
        CompositeWordBookRepository(
            AssetWordBookDataSource(this, searchRepository),
            RoomWordBookDataSource(database.wordBookDao(), searchRepository),
        )
    }
    val courseRepository by lazy { CourseRepository(wordBookRepository) }
    val learningRepository by lazy { LearningRepository(database.learningDao()) }
    val multiAgentCoordinator by lazy { MultiAgentCoordinator(agentRepository, searchRepository) }
    val speechRepository by lazy {
        SpeechRepository.create(
            BuildConfig.SPEECH_BACKEND_BASE_URL,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            dataInitializer.initialize()
            wordBookAssetImporter.importIfNeeded()
            textbookAssetImporter.importIfNeeded()
        }
    }
}
