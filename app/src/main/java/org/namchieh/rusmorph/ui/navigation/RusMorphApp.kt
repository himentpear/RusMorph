package org.namchieh.rusmorph.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.RusMorphApplication
import org.namchieh.rusmorph.data.local.InitializationState
import org.namchieh.rusmorph.domain.learning.LearningUnitType
import org.namchieh.rusmorph.domain.grammar.QuestionEntryContextType
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.namchieh.rusmorph.ui.AgentViewModel
import org.namchieh.rusmorph.ui.CommandViewModel
import org.namchieh.rusmorph.ui.LocalExplanationViewModel
import org.namchieh.rusmorph.ui.PronunciationViewModel
import org.namchieh.rusmorph.ui.conversation.ConversationViewModel
import org.namchieh.rusmorph.ui.screen.conversation.ConversationScreen
import org.namchieh.rusmorph.ui.SearchViewModel
import org.namchieh.rusmorph.ui.WordDetailViewModel
import org.namchieh.rusmorph.ui.learning.CourseDetailViewModel
import org.namchieh.rusmorph.ui.learning.CoursesViewModel
import org.namchieh.rusmorph.ui.learning.DialogueViewModel
import org.namchieh.rusmorph.ui.learning.LearningSummaryViewModel
import org.namchieh.rusmorph.ui.learning.LessonDetailViewModel
import org.namchieh.rusmorph.ui.learning.TextDetailViewModel
import org.namchieh.rusmorph.ui.learning.VocabularyViewModel
import org.namchieh.rusmorph.ui.grammar.GrammarHomeViewModel
import org.namchieh.rusmorph.ui.grammar.GrammarDetailViewModel
import org.namchieh.rusmorph.ui.grammar.Tem4PracticeViewModel
import org.namchieh.rusmorph.ui.grammar.QuestionRunnerViewModel
import org.namchieh.rusmorph.ui.screen.agent.AgentScreen
import org.namchieh.rusmorph.ui.screen.cards.CommandScreen
import org.namchieh.rusmorph.ui.screen.detail.WordDetailScreen
import org.namchieh.rusmorph.ui.screen.explanation.LocalExplanationScreen
import org.namchieh.rusmorph.ui.screen.initialization.InitializationScreen
import org.namchieh.rusmorph.ui.screen.learning.CourseDetailScreen
import org.namchieh.rusmorph.ui.screen.learning.CoursesScreen
import org.namchieh.rusmorph.ui.screen.learning.DialogueScreen
import org.namchieh.rusmorph.ui.screen.learning.HomeScreen
import org.namchieh.rusmorph.ui.screen.learning.LearningQuickNavigation
import org.namchieh.rusmorph.ui.screen.learning.LocalLearningQuickNavigation
import org.namchieh.rusmorph.ui.screen.learning.LessonDetailScreen
import org.namchieh.rusmorph.ui.screen.learning.ProfileScreen
import org.namchieh.rusmorph.ui.screen.learning.ReviewScreen
import org.namchieh.rusmorph.ui.screen.learning.ReviewQueueScreen
import org.namchieh.rusmorph.ui.screen.learning.VocabularyScreen
import org.namchieh.rusmorph.ui.screen.learning.UnavailableContentScreen
import org.namchieh.rusmorph.ui.screen.learning.text.TextDetailScreen
import org.namchieh.rusmorph.ui.screen.placeholder.PlaceholderScreen
import org.namchieh.rusmorph.ui.screen.pronunciation.PronunciationScreen
import org.namchieh.rusmorph.ui.screen.search.SearchScreen
import org.namchieh.rusmorph.ui.screen.settings.SettingsScreen
import org.namchieh.rusmorph.ui.screen.settings.WallpaperScreen
import org.namchieh.rusmorph.ui.screen.grammar.GrammarHomeScreen
import org.namchieh.rusmorph.ui.screen.grammar.GrammarDetailScreen
import org.namchieh.rusmorph.ui.screen.grammar.Tem4PracticeScreen
import org.namchieh.rusmorph.ui.screen.grammar.QuestionRunnerScreen
import org.namchieh.rusmorph.update.ui.AppUpdateDialog

@Composable
fun RusMorphApp(application: RusMorphApplication) {
    val navController = rememberNavController()
    val initializationState by application.dataInitializer.state.collectAsState()
    val updateState by application.updateCoordinator.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    fun selectBottom(destination: BottomDestination) {
        navController.navigate(destination.route) {
            popUpTo(Routes.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openUnit(lesson: org.namchieh.rusmorph.domain.learning.Lesson, type: LearningUnitType) {
        coroutineScope.launch {
            application.learningRepository.saveProgress(
                org.namchieh.rusmorph.domain.learning.LearningProgress(
                    sourceId = "${lesson.id}-${type.name.lowercase()}",
                    courseId = lesson.courseId,
                    lessonId = lesson.id,
                    unitType = type,
                    progress = 0f,
                    status = org.namchieh.rusmorph.domain.learning.LearningStatus.IN_PROGRESS,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        when (type) {
            LearningUnitType.VOCABULARY -> navController.navigate(Routes.vocabulary(lesson.id))
            LearningUnitType.DIALOGUE -> navController.navigate(Routes.dialogue("dialogue-${lesson.id}"))
            LearningUnitType.TEXT -> navController.navigate(Routes.text("text-${lesson.id}"))
            LearningUnitType.REVIEW -> selectBottom(BottomDestination.Review)
            else -> Unit
        }
    }

    LaunchedEffect(initializationState) {
        if (initializationState is InitializationState.Ready) {
            delay(1_500)
            application.updateCoordinator.checkAutomatically()
        }
    }

    Surface {
        Box {
            CompositionLocalProvider(
                LocalLearningQuickNavigation provides LearningQuickNavigation(
                    back = { navController.navigateUp() },
                    learning = {
                        navController.navigate(Routes.Learning) {
                            popUpTo(Routes.Home)
                            launchSingleTop = true
                        }
                    },
                    home = {
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Home)
                            launchSingleTop = true
                        }
                    },
                ),
            ) {
            NavHost(navController, startDestination = Routes.Initialization) {
            composable(Routes.Initialization) {
                LaunchedEffect(initializationState) {
                    if (initializationState is InitializationState.Ready) navController.navigate(Routes.Home) {
                        popUpTo(Routes.Initialization) { inclusive = true }; launchSingleTop = true
                    }
                }
                InitializationScreen(initializationState, { coroutineScope.launch { application.dataInitializer.initialize() } })
            }
            composable(Routes.Home) {
                val wallpaper by application.appSettings.wallpaper.collectAsState()
                val coursesVm: CoursesViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { CoursesViewModel(application.courseRepository) } } })
                val summaryVm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.learningRepository, application.appSettings, application.courseRepository) } } })
                val courses by coursesVm.state.collectAsState()
                val stats by summaryVm.stats.collectAsState()
                val progress by summaryVm.progress.collectAsState()
                val activeCourseId by summaryVm.activeCourseId.collectAsState()

                LaunchedEffect(activeCourseId) {
                    summaryVm.checkAndAutoAdvanceDaily(activeCourseId)
                }

                HomeScreen(
                    coursesState = courses,
                    stats = stats,
                    progress = progress,
                    selectedCourseId = activeCourseId,
                    onCourse = { navController.navigate(Routes.course(it)) },
                    onContinue = { course, lesson, unitType ->
                        when (unitType) {
                            LearningUnitType.VOCABULARY -> navController.navigate(Routes.vocabulary(lesson))
                            LearningUnitType.DIALOGUE -> navController.navigate(Routes.dialogue("dialogue-$lesson"))
                            LearningUnitType.TEXT -> navController.navigate(Routes.text("text-$lesson"))
                            else -> navController.navigate(Routes.lesson(course, lesson))
                        }
                    },
                    onReview = { navController.navigate(Routes.ReviewSession) },
                    onBottom = ::selectBottom,
                    onAI = { navController.navigate(Routes.Commands) },
                    wallpaper = wallpaper,
                )
            }
            composable(Routes.Learning) {
                val vm: CoursesViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { CoursesViewModel(application.courseRepository) } } })
                val summaryVm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.learningRepository, application.appSettings, application.courseRepository) } } })
                val state by vm.state.collectAsState()
                val activeCourseId by summaryVm.activeCourseId.collectAsState()
                val progress by summaryVm.progress.collectAsState()
                org.namchieh.rusmorph.ui.screen.learning.LearningScreen(
                    coursesState = state,
                    activeCourseId = activeCourseId,
                    progress = progress,
                    onSelectCourse = summaryVm::selectCourse,
                    onCourse = { navController.navigate(Routes.course(it)) },
                    onPronunciation = { navController.navigate(Routes.Pronunciation) },
                    onAiCommands = { navController.navigate(Routes.Commands) },
                    onConversation = { navController.navigate(Routes.Conversation) },
                    onGrammar = { navController.navigate(Routes.Grammar) },
                    onTem4 = { navController.navigate(Routes.Tem4) },
                    onBottom = ::selectBottom,
                )
            }
            composable(Routes.Courses) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.navigate(Routes.Learning) { popUpTo(Routes.Courses) { inclusive = true } }
                }
            }
            composable(Routes.Dictionary) {
                val factory = remember(application) { viewModelFactory { initializer { SearchViewModel(application.searchRepository, application.dataInitializer, createSavedStateHandle(), application.speechRepository) } } }
                val vm: SearchViewModel = viewModel(factory = factory)
                SearchScreen(
                    vm, { navController.navigate(Routes.word(it)) }, { navController.navigate(Routes.commands(it)) },
                    { navController.navigate(Routes.Settings) }, { navController.navigate(Routes.Pronunciation) }, ::selectBottom,
                    selectedDestination = BottomDestination.Dictionary,
                )
            }
            composable(Routes.Review) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.learningRepository, application.appSettings, application.courseRepository) } } })
                val stats by vm.stats.collectAsState()
                ReviewScreen(stats, { navController.navigate(Routes.ReviewSession) }, ::selectBottom)
            }
            composable(Routes.ReviewSession) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.learningRepository, application.appSettings, application.courseRepository) } } })
                val items by vm.dueReviews.collectAsState()
                ReviewQueueScreen(items, { navController.navigate(Routes.word(it)) }, navController::navigateUp)
            }
            composable(Routes.Profile) {
                val vm: LearningSummaryViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { LearningSummaryViewModel(application.learningRepository, application.appSettings, application.courseRepository) } } })
                val stats by vm.stats.collectAsState()
                ProfileScreen(stats, { navController.navigate(Routes.Settings) }, ::selectBottom)
            }
            composable(Routes.CoursePattern, listOf(navArgument("courseId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("courseId"))
                val vm: CourseDetailViewModel = viewModel(key = "course-$id", factory = remember(application, id) { viewModelFactory { initializer { CourseDetailViewModel(application.courseRepository, id) } } })
                val course by vm.course.collectAsState(); val lessons by vm.lessons.collectAsState()
                CourseDetailScreen(course, lessons, ::openUnit, navController::navigateUp)
            }
            composable(Routes.LessonPattern, listOf(navArgument("courseId") { type = NavType.StringType }, navArgument("lessonId") { type = NavType.StringType })) { entry ->
                val courseId = checkNotNull(entry.arguments?.getString("courseId")); val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: LessonDetailViewModel = viewModel(key = "lesson-$lessonId", factory = remember(application, courseId, lessonId) { viewModelFactory { initializer { LessonDetailViewModel(application.courseRepository, courseId, lessonId) } } })
                val state by vm.state.collectAsState()
                LaunchedEffect(courseId, lessonId) {
                    application.learningRepository.saveProgress(
                        org.namchieh.rusmorph.domain.learning.LearningProgress(
                            sourceId = lessonId, courseId = courseId, lessonId = lessonId, unitType = null,
                            progress = 0f, status = org.namchieh.rusmorph.domain.learning.LearningStatus.IN_PROGRESS,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                LessonDetailScreen(state, ::openUnit, navController::navigateUp, { navController.navigate(Routes.commands("总结本课 ${lessonId.substringAfterLast('-')}")) })
            }
            composable(Routes.VocabularyPattern, listOf(navArgument("lessonId") { type = NavType.StringType })) { entry ->
                val lessonId = checkNotNull(entry.arguments?.getString("lessonId"))
                val vm: VocabularyViewModel = viewModel(
                    key = "vocab-$lessonId",
                    factory = remember(application, lessonId) {
                        viewModelFactory {
                            initializer {
                                VocabularyViewModel(application.courseRepository, lessonId, application.learningRepository)
                            }
                        }
                    },
                )
                val state by vm.state.collectAsState()
                val enrolledIds by vm.enrolledIds.collectAsState()
                VocabularyScreen(
                    state = state,
                    lessonId = lessonId,
                    enrolledSourceIds = enrolledIds,
                    onWord = { navController.navigate(Routes.word(it)) },
                    onAddReview = { entryId -> vm.addWord(entryId) },
                    onRemoveReview = { entryId -> vm.removeWord(entryId) },
                    onBatchAddReview = { entryIds -> vm.batchAddWords(entryIds) },
                    onWordAI = { navController.navigate(Routes.agent(it, org.namchieh.rusmorph.agent.AgentQuestionType.CUSTOM)) },
                    onBack = navController::navigateUp,
                    onAI = { navController.navigate(Routes.commands("解释第 ${lessonId.substringAfterLast('-')} 课词汇")) },
                )
            }
            composable(Routes.DialoguePattern, listOf(navArgument("dialogueId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("dialogueId"))
                val vm: DialogueViewModel = viewModel(key = id, factory = remember(application, id) { viewModelFactory { initializer { DialogueViewModel(application.courseRepository, id) } } })
                val state by vm.state.collectAsState()
                DialogueScreen(state, { text, source -> navController.navigate(Routes.pronunciation(text, "DIALOGUE_LINE", source)) }, navController::navigateUp, { navController.navigate(Routes.commands("解释这段教材对话")) })
            }
            composable(Routes.Grammar) {
                val vm: GrammarHomeViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { GrammarHomeViewModel(application.grammarRepository) } } })
                val points by vm.points.collectAsState()
                GrammarHomeScreen(points, { navController.navigate(Routes.grammar(it)) }, { navController.navigate(Routes.Tem4) }, navController::navigateUp)
            }
            composable(Routes.GrammarPattern, listOf(navArgument("pointId") { type = NavType.StringType })) { entry ->
                val pointId = checkNotNull(entry.arguments?.getString("pointId"))
                val vm: GrammarDetailViewModel = viewModel(
                    key = "grammar-$pointId",
                    factory = remember(application, pointId) { viewModelFactory { initializer { GrammarDetailViewModel(application.grammarRepository, pointId) } } },
                )
                val state by vm.state.collectAsState()
                GrammarDetailScreen(state) { questionId, targetPointId ->
                    navController.navigate(Routes.question(questionId, QuestionRunnerMode.LEARNING, QuestionEntryContextType.GRAMMAR, targetPointId))
                }
            }
            composable(Routes.Tem4) {
                val vm: Tem4PracticeViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { Tem4PracticeViewModel(application.examPracticeRepository, application.grammarRepository) } } })
                val questions by vm.questions.collectAsState()
                val wrongQuestions by vm.wrongQuestions.collectAsState()
                val grammarPoints by vm.grammarPoints.collectAsState()
                Tem4PracticeScreen(
                    questions, wrongQuestions, grammarPoints,
                    { questionId, pointId -> navController.navigate(Routes.question(questionId, QuestionRunnerMode.PRACTICE, QuestionEntryContextType.TEM4, pointId)) },
                    { navController.navigate(Routes.grammar(it)) },
                    navController::navigateUp,
                )
            }
            composable(
                Routes.QuestionPattern,
                listOf(
                    navArgument("questionId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = QuestionRunnerMode.PRACTICE.name },
                    navArgument("context") { type = NavType.StringType; defaultValue = QuestionEntryContextType.TEM4.name },
                    navArgument("pointId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val questionId = checkNotNull(entry.arguments?.getString("questionId"))
                val mode = runCatching { QuestionRunnerMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }.getOrDefault(QuestionRunnerMode.PRACTICE)
                val context = runCatching { QuestionEntryContextType.valueOf(entry.arguments?.getString("context").orEmpty()) }.getOrDefault(QuestionEntryContextType.TEM4)
                val pointId = entry.arguments?.getString("pointId")?.takeIf(String::isNotBlank)
                val vm: QuestionRunnerViewModel = viewModel(
                    key = "question-$questionId-$mode-$pointId",
                    factory = remember(application, questionId, mode, context, pointId) { viewModelFactory { initializer {
                        QuestionRunnerViewModel(
                            createSavedStateHandle(), application.examPracticeRepository, application.grammarRepository,
                            application.agentRepository, questionId, mode, context, pointId,
                        )
                    } } },
                )
                val state by vm.state.collectAsState()
                QuestionRunnerScreen(
                    state = state,
                    mode = mode,
                    onSelect = vm::selectAnswer,
                    onSubmit = vm::submit,
                    onPeek = vm::openGrammarPeek,
                    onClosePeek = vm::closeGrammarPeek,
                    onFullGrammar = { navController.navigate(Routes.grammar(it)) },
                    onNext = { target -> coroutineScope.launch {
                        application.examPracticeRepository.getNextQuestion(questionId, target)?.let { next ->
                            navController.navigate(Routes.question(next.questionId, mode, if (target != null) QuestionEntryContextType.GRAMMAR else context, target))
                        }
                    } },
                    onGenerateVariant = vm::generateVariant,
                    onGeneratedQuestion = { generatedId ->
                        vm.consumeGeneratedQuestion()
                        val generatedPointId = state.grammarPoints.firstOrNull()?.pointId ?: pointId
                        navController.navigate(Routes.question(generatedId, QuestionRunnerMode.AI_VARIANT, QuestionEntryContextType.AI_VARIANT, generatedPointId))
                    },
                )
            }
            composable(Routes.TextPattern, listOf(navArgument("textId") { type = NavType.StringType })) { entry ->
                val id = checkNotNull(entry.arguments?.getString("textId"))
                val vm: TextDetailViewModel = viewModel(
                    key = id,
                    factory = remember(application, id) { viewModelFactory { initializer { TextDetailViewModel(application.courseRepository, id) } } },
                )
                val state by vm.state.collectAsState()
                TextDetailScreen(state, navController::navigateUp) {
                    navController.navigate(Routes.commands("解释这篇教材课文"))
                }
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    application.apiDiagnostics,
                    application.agentRepository,
                    application.appSettings,
                    application.updateCoordinator,
                    navController::navigateUp,
                    { navController.navigate(Routes.Wallpaper) },
                )
            }
            composable(Routes.Conversation) {
                val vm: ConversationViewModel = viewModel(factory = remember(application) {
                    viewModelFactory { initializer { ConversationViewModel(createSavedStateHandle(), application.conversationRepository) } }
                })
                ConversationScreen(vm, navController::navigateUp)
            }
            composable(Routes.Wallpaper) {
                WallpaperScreen(application.appSettings, navController::navigateUp)
            }
            composable(
                Routes.PronunciationPattern,
                listOf(
                    navArgument("target") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("type") { type = NavType.StringType; defaultValue = "FREE" },
                    navArgument("sourceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("lessonId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val target = entry.arguments?.getString("target")
                val type = entry.arguments?.getString("type") ?: "FREE"
                val sourceId = entry.arguments?.getString("sourceId")?.takeIf { it.isNotBlank() }
                val lessonId = entry.arguments?.getString("lessonId")?.takeIf { it.isNotBlank() }
                val vm: PronunciationViewModel = viewModel(
                    key = "pronunciation-${sourceId ?: "free"}",
                    factory = remember(application, type, sourceId, lessonId) { viewModelFactory { initializer {
                        PronunciationViewModel(
                            application.speechRepository, application.agentRepository, application.learningRepository,
                            runCatching { org.namchieh.rusmorph.domain.learning.PronunciationSessionType.valueOf(type) }.getOrDefault(org.namchieh.rusmorph.domain.learning.PronunciationSessionType.FREE),
                            sourceId, lessonId,
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
                val vm: CommandViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { CommandViewModel(createSavedStateHandle(), application.multiAgentCoordinator, application.localLibraryRepository, application.appSettings) } } })
                CommandScreen(vm, navController::navigateUp)
            }
            composable(Routes.WordPattern, listOf(navArgument("entryId") { type = NavType.StringType })) {
                val vm: WordDetailViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { val handle = createSavedStateHandle(); WordDetailViewModel(application.searchRepository, checkNotNull(handle["entryId"]), application.speechRepository, application.learningRepository) } } })
                WordDetailScreen(
                    viewModel = vm,
                    onBack = navController::navigateUp,
                    onExplanationClick = { navController.navigate(Routes.explanation(it)) },
                    onAgentClick = { id, type -> navController.navigate(Routes.agent(id, type)) },
                    onNavigateToRule = { category, ruleId -> navController.navigate(Routes.morphologyRules(category, ruleId)) },
                )
            }
            composable(
                route = Routes.MorphologyRulesPattern,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("ruleId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
                enterTransition = { fadeIn(tween(240)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                val ruleId = backStackEntry.arguments?.getString("ruleId")
                org.namchieh.rusmorph.ui.screen.tools.MorphologyRulesScreen(
                    initialCategory = category,
                    initialRuleId = ruleId,
                    onBack = navController::navigateUp,
                )
            }
            composable(
                route = Routes.MorphologyRules,
                enterTransition = { fadeIn(tween(240)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                org.namchieh.rusmorph.ui.screen.tools.MorphologyRulesScreen(
                    onBack = navController::navigateUp,
                )
            }
            composable(Routes.AgentPattern, listOf(navArgument("entryId") { type = NavType.StringType }, navArgument("questionType") { type = NavType.StringType; defaultValue = "CUSTOM" })) {
                val vm: AgentViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { AgentViewModel(createSavedStateHandle(), application.knowledgeRetriever, application.agentContextBuilder, application.agentRepository) } } })
                AgentScreen(vm, navController::navigateUp)
            }
            composable(Routes.ExplanationPattern, listOf(navArgument("chunkId") { type = NavType.StringType })) {
                val vm: LocalExplanationViewModel = viewModel(factory = remember(application) { viewModelFactory { initializer { val handle = createSavedStateHandle(); LocalExplanationViewModel(application.searchRepository, checkNotNull(handle["chunkId"])) } } })
                LocalExplanationScreen(vm, navController::navigateUp)
            }
            listOf(Routes.Decks, Routes.Favorites).forEach { route -> composable(route) { PlaceholderScreen(BottomDestination.Profile, { selectBottom(it) }) } }
            }
            }
            AppUpdateDialog(
                state = updateState,
                onLater = application.updateCoordinator::remindLater,
                onSkip = application.updateCoordinator::skipVersion,
                onDownload = application.updateCoordinator::startDownload,
                onInstall = application.updateCoordinator::installDownloaded,
                onOpenPermission = application.updateCoordinator::openInstallPermission,
            )
        }
    }
}

fun destinationForInitialization(state: InitializationState): String? = Routes.Home.takeIf { state is InitializationState.Ready }
