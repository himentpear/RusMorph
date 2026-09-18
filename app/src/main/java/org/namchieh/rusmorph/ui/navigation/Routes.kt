package org.namchieh.rusmorph.ui.navigation

import android.net.Uri

object Routes {
    const val Initialization = "initialization"
    const val Home = "home"
    const val Courses = "courses"
    const val WordBooks = Courses
    const val Dictionary = "dictionary"
    const val Review = "review"
    const val ReviewSession = "review/session"
    const val Profile = "profile"
    const val WordBookPattern = "wordbook/{wordBookId}"
    const val LegacyCoursePattern = "course/{courseId}"
    const val LessonPattern = "lesson/{wordBookId}/{lessonId}"
    const val VocabularyPattern = "vocabulary/{wordBookId}/{lessonId}"
    const val LegacyVocabularyPattern = "vocabulary/{lessonId}"
    const val DialoguePattern = "dialogue/{wordBookId}/{lessonId}/{dialogueId}"
    const val GrammarPattern = "grammar/{grammarId}"
    const val TextPattern = "text/{wordBookId}/{lessonId}/{textId}"
    const val WordPattern = "word/{entryId}"
    const val ExplanationPattern = "local-explanation/{chunkId}"
    const val AgentPattern = "agent/{entryId}?questionType={questionType}"
    const val Commands = "agent-commands"
    const val CommandsPattern = "agent-commands?command={command}"
    const val Decks = "decks"
    const val Favorites = "favorites"
    const val Settings = "settings"
    const val Pronunciation = "pronunciation"
    const val PronunciationPattern = "pronunciation?target={target}&type={type}&sourceId={sourceId}&lessonId={lessonId}&wordBookId={wordBookId}"

    @Deprecated("Use Dictionary") const val Search = Dictionary
    @Deprecated("Use Profile") const val Mine = Profile

    fun wordBook(wordBookId: String) = "wordbook/${Uri.encode(wordBookId)}"
    fun lesson(wordBookId: String, lessonId: String) = "lesson/${Uri.encode(wordBookId)}/${Uri.encode(lessonId)}"
    fun vocabulary(wordBookId: String, lessonId: String) = "vocabulary/${Uri.encode(wordBookId)}/${Uri.encode(lessonId)}"
    fun dialogue(wordBookId: String, lessonId: String, dialogueId: String) = "dialogue/${Uri.encode(wordBookId)}/${Uri.encode(lessonId)}/${Uri.encode(dialogueId)}"
    fun grammar(grammarId: String) = "grammar/${Uri.encode(grammarId)}"
    fun text(wordBookId: String, lessonId: String, textId: String) = "text/${Uri.encode(wordBookId)}/${Uri.encode(lessonId)}/${Uri.encode(textId)}"
    fun pronunciation(target: String, type: String, sourceId: String? = null, lessonId: String? = null, wordBookId: String? = null) =
        "pronunciation?target=${Uri.encode(target)}&type=${Uri.encode(type)}&sourceId=${Uri.encode(sourceId.orEmpty())}&lessonId=${Uri.encode(lessonId.orEmpty())}&wordBookId=${Uri.encode(wordBookId.orEmpty())}"
    fun word(entryId: String) = "word/${Uri.encode(entryId)}"
    fun explanation(chunkId: String) = "local-explanation/${Uri.encode(chunkId)}"
    fun agent(entryId: String, questionType: org.namchieh.rusmorph.agent.AgentQuestionType) = "agent/${Uri.encode(entryId)}?questionType=${questionType.name}"
    fun commands(command: String? = null) = command?.takeIf { it.isNotBlank() }?.let { "agent-commands?command=${Uri.encode(it)}" } ?: Commands

    @Deprecated("Use WordBookPattern") const val CoursePattern = LegacyCoursePattern
    @Deprecated("Use wordBook") fun course(courseId: String) = "course/${Uri.encode(courseId)}"
}
