package org.namchieh.rusmorph.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.namchieh.rusmorph.agent.*
import org.namchieh.rusmorph.ui.screen.cards.WordCardView
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class WordCardComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardFlipsAndDistinguishesGeneratedFromSourceExamples() {
        val card = WordCard(
            id = "c", entryId = "e", word = "сло́во", normalizedWord = "слово",
            meanings = listOf("词"), partsOfSpeech = listOf("名词"), morphology = "中性",
            generatedSentences = listOf(GeneratedSentence("Это новое слово.", "这是一个新词。", EvidenceType.MODEL_GENERATED_EXAMPLE)),
            sourceExamples = listOf(GeneratedSentence("Слово дано в тексте.", null, EvidenceType.SOURCE_EXAMPLE)),
            evidence = listOf(CardEvidence(EvidenceType.LEXICON_FIELD, "本地词表")),
        )
        compose.setContent { RusMorphTheme { WordCardView(card, {}) } }
        compose.onNodeWithText("сло́во").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看卡片背面").performClick()
        compose.mainClock.advanceTimeBy(600)
        listOf("AI 生成例句", "教材例句", "本地词表 · 本地词表").forEach { compose.onNodeWithText(it, substring = true).assertIsDisplayed() }
        compose.onNodeWithContentDescription("返回卡片正面").assertIsDisplayed()
    }
}
