package org.namchieh.rusmorph.data.repository

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.namchieh.rusmorph.data.remote.VariantQuestionResponseDto

class VariantQuestionValidatorTest {
    private fun response(
        options: Map<String, String> = mapOf("A" to "a", "B" to "b", "C" to "c", "D" to "d"),
        answer: String = "A",
        pointId: String = "G1",
        stem: String = "Stem",
        analysis: String = "Analysis",
    ) = VariantQuestionResponseDto(stem, options, answer, analysis, pointId)

    @Test fun validJsonShapeAccepted() = assertNotNull(VariantQuestionValidator.validate(response(), "G1"))
    @Test fun missingOptionRejected() = assertNull(VariantQuestionValidator.validate(response(options = mapOf("A" to "a", "B" to "b", "C" to "c")), "G1"))
    @Test fun invalidAnswerRejected() = assertNull(VariantQuestionValidator.validate(response(answer = "E"), "G1"))
    @Test fun wrongPointRejected() = assertNull(VariantQuestionValidator.validate(response(pointId = "G2"), "G1"))
    @Test fun emptyContentRejected() = assertNull(VariantQuestionValidator.validate(response(stem = "", analysis = ""), "G1"))
}
