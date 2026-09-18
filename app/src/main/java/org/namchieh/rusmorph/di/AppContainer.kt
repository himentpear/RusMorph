package org.namchieh.rusmorph.di

import android.content.Context
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.RusMorphApplication
import org.namchieh.rusmorph.agent.AgentContextBuilder
import org.namchieh.rusmorph.agent.MultiAgentCoordinator
import org.namchieh.rusmorph.data.diagnostics.ApiDiagnostics
import org.namchieh.rusmorph.data.local.AssetDatabaseImporter
import org.namchieh.rusmorph.data.local.RusMorphDatabase
import org.namchieh.rusmorph.data.repository.AssetWordBookRepository
import org.namchieh.rusmorph.data.repository.CompositeWordBookRepository
import org.namchieh.rusmorph.data.repository.RoomWordBookRepository
import org.namchieh.rusmorph.data.repository.WordBookRepository
import org.namchieh.rusmorph.data.repository.DefaultAgentRepository
import org.namchieh.rusmorph.data.repository.KnowledgeRetriever
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.data.repository.LocalLibraryRepository
import org.namchieh.rusmorph.data.repository.RoomSearchDataSource
import org.namchieh.rusmorph.data.repository.SearchRepository
import org.namchieh.rusmorph.data.repository.SpeechRepository
import org.namchieh.rusmorph.data.settings.AppSettings

/**
 * 应用级依赖的唯一构造点。当前是手工 Service Locator；
 * 将来迁移 Hilt 时以此类为边界替换为依赖图，调用方无需改动。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RusMorphDatabase by lazy { RusMorphDatabase.create(appContext) }
    val dataInitializer: AssetDatabaseImporter by lazy { AssetDatabaseImporter(appContext, database) }
    val searchRepository: SearchRepository by lazy { SearchRepository(RoomSearchDataSource(database.searchDao())) }
    val knowledgeRetriever: KnowledgeRetriever by lazy { KnowledgeRetriever(searchRepository) }
    val agentContextBuilder: AgentContextBuilder by lazy { AgentContextBuilder() }
    val apiDiagnostics: ApiDiagnostics by lazy { ApiDiagnostics(appContext) }
    val appSettings: AppSettings by lazy { AppSettings(appContext) }
    val agentRepository by lazy { DefaultAgentRepository.create(BuildConfig.AGENT_PROXY_BASE_URL, apiDiagnostics) }
    val localLibraryRepository: LocalLibraryRepository by lazy { LocalLibraryRepository(database.localLibraryDao()) }
    val wordBookRepository: WordBookRepository by lazy {
        CompositeWordBookRepository(
            builtIn = AssetWordBookRepository(appContext, searchRepository),
            imported = RoomWordBookRepository(database.wordBookDao(), searchRepository),
        )
    }
    val learningRepository: LearningRepository by lazy { LearningRepository(database.learningDao()) }
    val multiAgentCoordinator: MultiAgentCoordinator by lazy { MultiAgentCoordinator(agentRepository, searchRepository) }
    val speechRepository: SpeechRepository by lazy { SpeechRepository.create(BuildConfig.SPEECH_BACKEND_BASE_URL) }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as RusMorphApplication).container
    }
}
