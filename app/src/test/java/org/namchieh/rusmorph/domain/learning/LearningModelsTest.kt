package org.namchieh.rusmorph.domain.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningModelsTest {
    @Test fun rolePlayRequiresReliableMultipleSpeakers() {
        val missing = Dialogue("d", "l", "t", null, listOf(DialogueLine("1", null, "Привет", null, null, 1)))
        assertFalse(missing.canRolePlay)
        val complete = Dialogue("d", "l", "t", null, listOf(
            DialogueLine("1", "Анна", "Привет", null, null, 1),
            DialogueLine("2", "Иван", "Здравствуйте", null, null, 2),
        ))
        assertTrue(complete.canRolePlay)
    }

    @Test fun rolePlayAdvancesPlaybackAndScoresOnlyTheSelectedRole() {
        val dialogue = Dialogue("d", "l", "t", null, listOf(
            DialogueLine("1", "Анна", "Привет", null, null, 1),
            DialogueLine("2", "Иван", "Здравствуйте", null, null, 2),
        ))
        val controller = DialoguePracticeController(dialogue, "Иван")
        controller.advancePlayback()
        val result = controller.submitLearnerScore(62.0)
        assertTrue(result.finished)
        assertTrue(result.needsRepeat.single().id == "2")
    }
}
