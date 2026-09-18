package org.namchieh.rusmorph.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.RusMorphApplication
import org.namchieh.rusmorph.data.local.InitializationState
import org.namchieh.rusmorph.domain.learning.LearningUnitType
import org.namchieh.rusmorph.ui.AgentViewModel
import org.namchieh.rusmorph.ui.CommandViewModel
import org.namchieh.rusmorph.ui.LocalExplanationViewModel
import org.namchieh.rusmorph.ui.PronunciationViewModel
import org.namchieh.rusmorph.ui.SearchViewModel
import org.namchieh.rusmorph.ui.WordDetailViewModel
import org.namchieh.rusmorph.ui.learning.WordBookDetailViewModel
import org.namchieh.rusmorph.ui.learning.WordBooksViewModel
import org.namchieh.rusmorph.ui.learning.DialogueViewModel
import org.namchieh.rusmorph.ui.learning.LearningSummaryViewModel
import org.namchieh.rusmorph.ui.learning.LessonDetailViewModel
import org.namchieh.rusmorph.ui.learning.VocabularyViewModel
import org.namchieh.rusmorph.ui.learning.TextViewModel
import org.namchieh.rusmorph.ui.screen.agent.AgentScreen
import org.namchieh.rusmorph.ui.screen.cards.CommandScreen
import org.namchieh.rusmorph.ui.screen.detail.WordDetailScreen
import org.namchieh.rusmorph.ui.screen.explanation.LocalExplanationScreen
import org.namchieh.rusmorph.ui.screen.initialization.InitializationScreen
import org.namchieh.rusmorph.ui.screen.learning.WordBookDetailScreen
import org.namchieh.rusmorph.ui.screen.learning.WordBooksScreen
import org.namchieh.rusmorph.ui.screen.learning.DialogueScreen
import org.namchieh.rusmorph.ui.screen.learning.HomeScreen
import org.namchieh.rusmorph.ui.screen.learning.LessonDetailScreen
import org.namchieh.rusmorph.ui.screen.learning.ProfileScreen
import org.namchieh.rusmorph.ui.screen.learning.ReviewScreen
import org.namchieh.rusmorph.ui.screen.learning.ReviewQueueScreen
import org.namchieh.rusmorph.ui.screen.learning.VocabularyScreen
import org.namchieh.rusmorph.ui.screen.learning.TextScreen
import org.namchieh.rusmorph.ui.screen.learning.UnavailableContentScreen
import org.namchieh.rusmorph.ui.screen.placeholder.PlaceholderScreen
import org.namchieh.rusmorph.ui.screen.pronunciation.PronunciationScreen
import org.namchieh.rusmorph.ui.screen.search.SearchScreen
import org.namchieh.rusmorph.ui.screen.settings.SettingsScreen

@Composable
fun RusMorphApp(application: RusMorphApplication) {
    val navController = rememberNavController()
    val initializationState by application.container.dataInitializer.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    fun selectBottom(destination: BottomDestination) {
        navController.navigate(destination.route) {
            popUpTo(Routes.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface {
        NavHost(navController, startDestination = Routes.Initialization) {
            composable(Routes.Initialization) {
                LaunchedEffect(initializationState) {
                    if (initializationState is InitializationState.Ready) navController.navigate(Routes.Home) {
                        popUpTo(Routes.Initialization) { inclusive = true }; launchSingleTop = true
                    }
                }
                InitializationScreen(initializationState, { coroutineScope.launch { application.container.dataInitializer.initialize() } })
            }
            composable(Routes.Home) {
                val wordBooksVm: WordBooksViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { WordBooksViewModel(application.container.wordBookRepository, application.container.learningRepository) } } })
                val summaryVm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.container.learningRepository) } } })
                val wordBooks by wordBooksVm.state.collectAsState()
                val stats by summaryVm.stats.collectAsState()
                val progress by summaryVm.progress.collectAsState()
                HomeScreen(
                    wordBooks, stats, progress,
                    onCourse = { navController.navigate(Routes.wordBook(it)) },
                    onContinue = { wordBook, lesson -> navController.navigate(Routes.lesson(wordBook, lesson)) },
                    onDictionary = { selectBottom(BottomDestination.Dictionary) },
                    onPronunciation = { navController.navigate(Routes.Pronunciation) },
                    onCourses = { selectBottom(BottomDestination.Courses) },
                    onReview = { selectBottom(BottomDestination.Review) },
                    onBottom = ::selectBottom,
                    onAI = { navController.navigate(Routes.Commands) },
                )
            }
            composable(Routes.Courses) {
                val vm: WordBooksViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { WordBooksViewModel(application.container.wordBookRepository, application.container.learningRepository) } } })
                val state by vm.state.collectAsState()
                WordBooksScreen(state, { navController.navigate(Routes.wordBook(it)) }, ::selectBottom)
            }
            composable(Routes.Dictionary) {
                val factory = remember(application) { viewModelFactory { initializer { SearchViewModel(application.container.searchRepository, application.container.dataInitializer, createSavedStateHandle(), application.container.speechRepository) } } }
                val vm: SearchViewModel = viewModel(factory = factory)
                SearchScreen(
                    vm, { navController.navigate(Routes.word(it)) }, { navController.navigate(Routes.commands(it)) },
                    { navController.navigate(Routes.Settings) }, { navController.navigate(Routes.Pronunciation) }, ::selectBottom,
                    selectedDestination = BottomDestination.Dictionary,
                )
            }
            composable(Routes.Review) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.container.learningRepository) } } })
                val stats by vm.stats.collectAsState()
                ReviewScreen(stats, { navController.navigate(Routes.ReviewSession) }, ::selectBottom)
            }
            composable(Routes.ReviewSession) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.container.learningRepository) } } })
                val items by vm.dueReviews.collectAsState()
                ReviewQueueScreen(items, { navController.navigate(Routes.word(it)) }, navController::navigateUp)
            }
            composable(Routes.Profile) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.container.learningRepository) } } })
                val stats by vm.stats.collectAsState()
                ProfileScreen(stats, { navController.navigate(Routes.Settings) }, ::selectBottom)
            }
            composable(Routes.WordBookPattern, listOf(navArgument("wordBookId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("wordBookId"))
                val vm: WordBookDetailViewModel = viewModel(key = "wordbook-$id", factory = remember(application, id) { viewModelFactory { initializer { WordBookDetailViewModel(application.container.wordBookRepository, id, application.container.learningRepository) } } })
                val wordBook by vm.course.collectAsState(); val lessons by vm.lessons.collectAsState()
                WordBookDetailScreen(wordBook, lessons, { wordBookId, lessonId -> navController.navigate(Routes.lesson(wordBookId, lessonId)) }, navController::navigateUp)
            }
            composable(Routes.LegacyCoursePattern, listOf(navArgument("courseId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("courseId"))
                LaunchedEffect(id) {
                    navController.navigate(Routes.wordBook(id)) { popUpTo(Routes.LegacyCoursePattern) { inclusive = true } }
                }
            }
            composable(Routes.LessonPattern, listOf(navArgument("wordBookId") { type = NavType.StringType }, navArgument("lessonId") { type = NavType.StringType })) { entry ->
                val wordBookId = checkNotNull(entry.arguments?.getString("wordBookId")); val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: LessonDetailViewModel = viewModel(key = "lesson-$wordBookId-$lessonId", factory = remember(application, wordBookId, lessonId) { viewModelFactory { initializer { LessonDetailViewModel(application.container.wordBookRepository, application.container.learningRepository, wordBookId, lessonId) } } })
                val state by vm.state.collectAsState()
                LessonDetailScreen(state, { lesson, unit ->
                    val sourceId = unit.sourceId
                    when (unit.type) {
                        LearningUnitType.VOCABULARY -> navController.navigate(Routes.vocabulary(lesson.wordBookId, lesson.id))
                        LearningUnitType.DIALOGUE -> sourceId?.let { navController.navigate(Routes.dialogue(lesson.wordBookId, lesson.id, it)) }
                        LearningUnitType.TEXT -> sourceId?.let { navController.navigate(Routes.text(lesson.wordBookId, lesson.id, it)) }
                        LearningUnitType.REVIEW -> selectBottom(BottomDestination.Review)
                        else -> Unit
                    }
                }, navController::navigateUp, { navController.navigate(Routes.commands("总结本课 ${lessonId.substringAfterLast('-')}")) })
            }
            composable(Routes.VocabularyPattern, listOf(navArgument("wordBookId") { type = NavType.StringType }, navArgument("lessonId") { type = NavType.StringType })) { entry ->
                val wordBookId = checkNotNull(entry.arguments?.getString("wordBookId"))
                val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: VocabularyViewModel = viewModel(key = "vocab-$wordBookId-$lessonId", factory = remember(application, wordBookId, lessonId) { viewModelFactory { initializer { VocabularyViewModel(application.container.wordBookRepository, application.container.learningRepository, wordBookId, lessonId) } } })
                val state by vm.state.collectAsState()
                VocabularyScreen(
                    state, lessonId,
                    onWord = { navController.navigate(Routes.word(it)) },
                    onAddReview = vm::addReview,
                    onWordAI = { navController.navigate(Routes.agent(it, org.namchieh.rusmorph.agent.AgentQuestionType.CUSTOM)) },
                    onBack = navController::navigateUp,
                    onAI = { navController.navigate(Routes.commands("解释第 ${lessonId.substringAfterLast('-')} 课词汇")) },
                )
            }
            composable(Routes.LegacyVocabularyPattern, listOf(navArgument("lessonId") { type = NavType.StringType })) { entry ->
                val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                LaunchedEffect(lessonId) {
                    navController.navigate(Routes.vocabulary(org.namchieh.rusmorph.data.repository.AssetWordBookRepository.WORD_BOOK_ID, lessonId)) {
                        popUpTo(Routes.LegacyVocabularyPattern) { inclusive = true }
                    }
                }
            }
            composable(Routes.DialoguePattern, listOf(navArgument("wordBookId") { type = NavType.StringType }, navArgument("lessonId") { type = NavType.StringType }, navArgument("dialogueId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("dialogueId"))
                val bookId = checkNotNull(entry.arguments?.getString("wordBookId"))
                val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: DialogueViewModel = viewModel(key = "$bookId/$lessonId/$id", factory = remember(application, bookId, lessonId, id) { viewModelFactory { initializer { DialogueViewModel(application.container.wordBookRepository, bookId, lessonId, id) } } })
                val state by vm.state.collectAsState()
                DialogueScreen(state, { text, source -> navController.navigate(Routes.pronunciation(text, "DIALOGUE_LINE", source, (state as? org.namchieh.rusmorph.ui.learning.Loadable.Content)?.value?.lessonId, (state as? org.namchieh.rusmorph.ui.learning.Loadable.Content)?.value?.wordBookId)) }, navController::navigateUp, { navController.navigate(Routes.commands("解释这段教材对话")) })
            }
            composable(Routes.GrammarPattern, listOf(navArgument("grammarId") { type = NavType.StringType })) { UnavailableContentScreen("语法", navController::navigateUp) }
            composable(Routes.TextPattern, listOf(navArgument("wordBookId") { type = NavType.StringType }, navArgument("lessonId") { type = NavType.StringType }, navArgument("textId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("textId"))
                val bookId = checkNotNull(entry.arguments?.getString("wordBookId"))
                val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: TextViewModel = viewModel(key = "$bookId/$lessonId/$id", factory = remember(application, bookId, lessonId, id) { viewModelFactory { initializer { TextViewModel(application.container.wordBookRepository, bookId, lessonId, id) } } })
                val state by vm.state.collectAsState()
                TextScreen(state, { text, source -> navController.navigate(Routes.pronunciation(text, "TEXT_SENTENCE", source, (state as? org.namchieh.rusmorph.ui.learning.Loadable.Content)?.value?.lessonId, (state as? org.namchieh.rusmorph.ui.learning.Loadable.Content)?.value?.wordBookId)) }, navController::navigateUp, { navController.navigate(Routes.commands("解释这篇教材课文")) })
            }
            composable(Routes.Settings) { SettingsScreen(application.container.apiDiagnostics, application.container.agentRepository, application.container.appSettings, navController::navigateUp) }
            composable(
                Routes.PronunciationPattern,
                listOf(
                    navArgument("target") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("type") { type = NavType.StringType; defaultValue = "FREE" },
                    navArgument("sourceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("lessonId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("wordBookId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val target = entry.arguments?.getString("target")
                val type = entry.arguments?.getString("type") ?: "FREE"
                val sourceId = entry.arguments?.getString("sourceId")?.takeIf { it.isNotBlank() }
                val lessonId = entry.arguments?.getString("lessonId")?.takeIf { it.isNotBlank() }
                val vm: PronunciationViewModel = viewModel(
                    key = "pronunciation-${sourceId ?: "free"}",
                    factory = remember(application, type, sourceId, lessonId, entry.arguments?.getString("wordBookId")) { viewModelFactory { initializer {
                        PronunciationViewModel(
                            application.container.speechRepository, application.container.agentRepository, application.container.learningRepository,
                            runCatching { org.namchieh.rusmorph.domain.learning.PronunciationSessionType.valueOf(type) }.getOrDefault(org.namchieh.rusmorph.domain.learning.PronunciationSessionType.FREE),
                            sourceId, lessonId, entry.arguments?.getString("wordBookId"),
                        )
                    } } },
                )
                LaunchedEffect(target) { if (!target.isNullOrBlank()) vm.setTargetText(target) }
                PronunciationScreen(vm, navController::navigateUp)
            }
            composable(
                route = Routes.CommandsPattern,
                arguments = listOf(navArgument("command") { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { fadeIn(tween(240)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                val vm: CommandViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { CommandViewModel(createSavedStateHandle(), application.container.multiAgentCoordinator, application.container.localLibraryRepository, application.container.appSettings) } } })
                CommandScreen(vm, navController::navigateUp)
            }
            composable(Routes.WordPattern, listOf(navArgument("entryId") { type = NavType.StringType })) {
                val vm: WordDetailViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { val handle = createSavedStateHandle(); WordDetailViewModel(application.container.searchRepository, checkNotNull(handle["entryId"])) } } })
                WordDetailScreen(vm, navController::navigateUp, { navController.navigate(Routes.explanation(it)) }, { id, type -> navController.navigate(Routes.agent(id, type)) })
            }
            composable(Routes.AgentPattern, listOf(navArgument("entryId") { type = NavType.StringType }, navArgument("questionType") { type = NavType.StringType; defaultValue = "CUSTOM" })) {
                val vm: AgentViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { AgentViewModel(createSavedStateHandle(), application.container.knowledgeRetriever, application.container.agentContextBuilder, application.container.agentRepository) } } })
                AgentScreen(vm, navController::navigateUp)
            }
            composable(Routes.ExplanationPattern, listOf(navArgument("chunkId") { type = NavType.StringType })) {
                val vm: LocalExplanationViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { val handle = createSavedStateHandle(); LocalExplanationViewModel(application.container.searchRepository, checkNotNull(handle["chunkId"])) } } })
                LocalExplanationScreen(vm, navController::navigateUp)
            }
            listOf(Routes.Decks, Routes.Favorites).forEach { route -> composable(route) { PlaceholderScreen(BottomDestination.Profile, { selectBottom(it) }) } }
        }
    }
}

fun destinationForInitialization(state: InitializationState): String? = Routes.Home.takeIf { state is InitializationState.Ready }
