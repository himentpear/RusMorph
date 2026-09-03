package org.namchieh.rusmorph.wordcard

import org.junit.Assert.*
import org.junit.Test
import org.namchieh.rusmorph.wordcard.agent.WordCardNormalizer
import org.namchieh.rusmorph.wordcard.agent.WordCardValidator
import org.namchieh.rusmorph.wordcard.data.FormAnalysisEngine
import org.namchieh.rusmorph.wordcard.model.*
import org.namchieh.rusmorph.wordcard.review.ReviewScheduler

class WordCardSystemTest {

    @Test
    fun testFormAnalysisEngine_acceptanceCases() {
        // Case 1: студентами -> студент (工具格 · 复数)
        val studentami = FormAnalysisEngine.analyze("студентами")
        assertTrue(studentami.isNotEmpty())
        assertEquals("студент", studentami.first().lemma)
        val analysis = studentami.first().analyses.first()
        assertEquals(GrammaticalCase.INSTRUMENTAL, analysis.grammaticalCase)
        assertEquals(GrammaticalNumber.PLURAL, analysis.number)

        // Case 2: окна -> окно (生格单数 与 主格复数 多义分析)
        val okna = FormAnalysisEngine.analyze("окна")
        assertTrue(okna.isNotEmpty())
        assertEquals("окно", okna.first().lemma)
        val oknaAnalyses = okna.first().analyses
        assertTrue(oknaAnalyses.size >= 2)
        assertTrue(oknaAnalyses.any { it.grammaticalCase == GrammaticalCase.GENITIVE && it.number == GrammaticalNumber.SINGULAR })
        assertTrue(oknaAnalyses.any { it.grammaticalCase == GrammaticalCase.NOMINATIVE && it.number == GrammaticalNumber.PLURAL })

        // Case 3: пошёл -> пойти (过去时 · 阳性)
        val poshel = FormAnalysisEngine.analyze("пошёл")
        assertTrue(poshel.isNotEmpty())
        assertEquals("пойти", poshel.first().lemma)
        assertEquals(Tense.PAST, poshel.first().analyses.first().tense)
        assertEquals(Gender.MASCULINE, poshel.first().analyses.first().gender)

        // Case 4: людей -> человек
        val lyudei = FormAnalysisEngine.analyze("людей")
        assertTrue(lyudei.isNotEmpty())
        assertEquals("человек", lyudei.first().lemma)
        assertEquals(GrammaticalNumber.PLURAL, lyudei.first().analyses.first().number)
    }

    @Test
    fun testSchemaValidator_validAndGracefulDegradation() {
        val sampleJson = """
        {
          "schema_version": "2.0",
          "lexeme": {
            "lemma": "студент",
            "display_form": "студе́нт",
            "language": "ru",
            "basic": {
              "part_of_speech": "noun",
              "gender": "masculine",
              "animacy": true,
              "cefr": "A1",
              "translations_zh": ["大学生", "学生"],
              "short_definition_zh": "高等学校的学生"
            },
            "pronunciation": {
              "stress_index": 5,
              "stress_pattern": "固定重音",
              "syllables": ["сту", "дент"]
            },
            "morphology": {
              "declension_type": "第一变格法（辅音结尾）",
              "stem": "студент",
              "declension": {
                "singular": {
                  "nominative": "студе́нт",
                  "genitive": "студе́нта",
                  "dative": "студе́нту",
                  "accusative": "студе́нта",
                  "instrumental": "студе́нтом",
                  "prepositional": "студе́нте"
                },
                "plural": {
                  "nominative": "студе́нты",
                  "genitive": "студе́нтов",
                  "dative": "студе́нтам",
                  "accusative": "студе́нтов",
                  "instrumental": "студе́нтами",
                  "prepositional": "студе́нтах"
                }
              }
            },
            "usage": {
              "example_ru": "Он учится хорошо, потому что он старательный студент.",
              "example_zh": "他学习很好，因为他是个用功的大学生。"
            },
            "learning": {
              "common_errors": ["注意结尾辅音 т 清晰发音，不要吞音"],
              "memory_hint": "源于拉丁语 studere（勤奋学习）"
            }
          },
          "form": {
            "input_form": "студентами",
            "display_form": "студе́нтами",
            "is_lemma": false,
            "analysis": {
              "case": "instrumental",
              "number": "plural",
              "gender": "masculine"
            },
            "explanation_zh": "工具格 · 复数"
          },
          "agent_meta": {
            "confidence": 0.98,
            "needs_review": false
          }
        }
        """.trimIndent()

        val validation = WordCardValidator.validateAndParse(sampleJson)
        assertTrue(validation is WordCardValidator.ValidationResult.Success)

        val success = validation as WordCardValidator.ValidationResult.Success
        val (lexeme, form) = WordCardNormalizer.normalizeFromDto(success.dto, "студентами")

        assertEquals("студент", lexeme.lemma)
        assertEquals("студе́нт", lexeme.displayForm)
        assertEquals("noun", lexeme.basic.partOfSpeech)
        assertEquals(Gender.MASCULINE, lexeme.basic.gender)
        assertEquals(true, lexeme.basic.animacy)
        assertEquals("A1", lexeme.basic.cefr)
        assertEquals("大学生", lexeme.basic.primaryTranslation)

        // Morphology check
        assertTrue(lexeme.morphology is Lexeme.MorphologyInfo.Noun)
        val noun = lexeme.morphology as Lexeme.MorphologyInfo.Noun
        assertEquals("студе́нт", noun.singular.nominative)
        assertEquals("студе́нтами", noun.plural.instrumental)

        // Form check
        assertEquals("студентами", form.inputForm)
        assertFalse(form.isLemma)
        assertEquals(GrammaticalCase.INSTRUMENTAL, form.primaryAnalysis?.grammaticalCase)
        assertEquals(GrammaticalNumber.PLURAL, form.primaryAnalysis?.number)
    }

    @Test
    fun testReviewScheduler_transitions() {
        val initial = ReviewState(lexemeId = "lex_test", mastery = 50, easeFactor = 2.5)

        // AGAIN drops mastery and reduces ease
        val againState = ReviewScheduler.computeNextState(initial, ReviewResult.AGAIN)
        assertEquals(30, againState.mastery)
        assertEquals(1, againState.wrongCount)
        assertTrue(againState.easeFactor < 2.5)

        // GOOD increases mastery and increases interval
        val goodState = ReviewScheduler.computeNextState(initial, ReviewResult.GOOD)
        assertEquals(65, goodState.mastery)
        assertEquals(1, goodState.correctCount)
        assertTrue(goodState.intervalDays >= 1.0)

        // EASY gives max boost
        val easyState = ReviewScheduler.computeNextState(initial, ReviewResult.EASY)
        assertEquals(75, easyState.mastery)
        assertTrue(easyState.easeFactor > 2.5)
    }
}
