package org.namchieh.rusmorph.ui.navigation

import android.net.Uri

object Routes {
    const val Initialization = "initialization"
    const val Home = "home"
    const val Courses = "courses"
    const val Dictionary = "dictionary"
    const val Review = "review"
    const val ReviewSession = "review/session"
    const val Profile = "profile"
    const val CoursePattern = "course/{courseId}"
    const val LessonPattern = "lesson/{courseId}/{lessonId}"
    const val VocabularyPattern = "vocabulary/{lessonId}"
    const val DialoguePattern = "dialogue/{dialogueId}"
    const val GrammarPattern = "grammar/{grammarId}"
    const val TextPattern = "text/{textId}"
    const val WordPattern = "word/{entryId}"
    const val ExplanationPattern = "local-explanation/{chunkId}"
    const val AgentPattern = "agent/{entryId}?questionType={questionType}"
    const val Commands = "agent-commands"
    const val CommandsPattern = "agent-commands?command={command}"
    const val Decks = "decks"
    const val Favorites = "favorites"
    const val Settings = "settings"
    const val Pronunciation = "pronunciation"
    const val PronunciationPattern = "pronunciation?target={target}&type={type}&sourceId={sourceId}&lessonId={lessonId}"

    @Deprecated("Use Dictionary") const val Search = Dictionary
    @Deprecated("Use Profile") const val Mine = Profile

    fun course(courseId: String) = "course/${Uri.encode(courseId)}"
    fun lesson(courseId: String, lessonId: String) = "lesson/${Uri.encode(courseId)}/${Uri.encode(lessonId)}"
    fun vocabulary(lessonId: String) = "vocabulary/${Uri.encode(lessonId)}"
    fun dialogue(dialogueId: String) = "dialogue/${Uri.encode(dialogueId)}"
    fun grammar(grammarId: String) = "grammar/${Uri.encode(grammarId)}"
    fun text(textId: String) = "text/${Uri.encode(textId)}"
    fun pronunciation(target: String, type: String, sourceId: String? = null, lessonId: String? = null) =
        "pronunciation?target=${Uri.encode(target)}&type=${Uri.encode(type)}&sourceId=${Uri.encode(sourceId.orEmpty())}&lessonId=${Uri.encode(lessonId.orEmpty())}"
    fun word(entryId: String) = "word/${Uri.encode(entryId)}"
    fun explanation(chunkId: String) = "local-explanation/${Uri.encode(chunkId)}"
    fun agent(entryId: String, questionType: org.namchieh.rusmorph.agent.AgentQuestionType) = "agent/${Uri.encode(entryId)}?questionType=${questionType.name}"
    fun commands(command: String? = null) = command?.takeIf { it.isNotBlank() }?.let { "agent-commands?command=${Uri.encode(it)}" } ?: Commands
}
