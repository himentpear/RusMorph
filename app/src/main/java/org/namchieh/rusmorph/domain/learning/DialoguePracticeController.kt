package org.namchieh.rusmorph.domain.learning

data class DialoguePracticeState(
    val dialogue: Dialogue,
    val selectedRole: String,
    val currentIndex: Int = 0,
    val completedLineIds: List<String> = emptyList(),
    val scores: Map<String, Double> = emptyMap(),
    val finished: Boolean = false,
) {
    val currentLine: DialogueLine? get() = dialogue.lines.getOrNull(currentIndex)
    val isLearnerTurn: Boolean get() = currentLine?.speaker == selectedRole
    val averageIntelligibility: Double? get() = scores.values.takeIf { it.isNotEmpty() }?.average()
    val needsRepeat: List<DialogueLine> get() = dialogue.lines.filter { (scores[it.id] ?: 100.0) < 70.0 }
}

class DialoguePracticeController(dialogue: Dialogue, selectedRole: String) {
    init {
        require(dialogue.canRolePlay) { "Role-play requires reliable speaker metadata" }
        require(dialogue.lines.any { it.speaker == selectedRole }) { "Selected role is not present in dialogue" }
    }

    var state = DialoguePracticeState(dialogue, selectedRole)
        private set

    fun advancePlayback(): DialoguePracticeState {
        check(!state.finished && !state.isLearnerTurn) { "Playback can only advance another speaker's line" }
        return advance(null)
    }

    fun submitLearnerScore(score: Double): DialoguePracticeState {
        check(!state.finished && state.isLearnerTurn) { "A score can only be submitted for the learner's line" }
        require(score in 0.0..100.0)
        return advance(score)
    }

    private fun advance(score: Double?): DialoguePracticeState {
        val line = checkNotNull(state.currentLine)
        val next = state.currentIndex + 1
        state = state.copy(
            currentIndex = next,
            completedLineIds = state.completedLineIds + line.id,
            scores = if (score == null) state.scores else state.scores + (line.id to score),
            finished = next >= state.dialogue.lines.size,
        )
        return state
    }
}
