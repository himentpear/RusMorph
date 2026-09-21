package org.namchieh.rusmorph.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.namchieh.rusmorph.domain.textbook.KnowledgeType

object WerusColors {
    val Red = Color(0xFFA33A32)
    val RedDark = Color(0xFF702822)
    val RedSoft = Color(0xFFF0DCD8)
    val Paper = Color(0xFFFFFCF7)
    val Canvas = Color(0xFFF7F4EE)
    val Beige = Color(0xFFF0E5D1)
    val BeigeMuted = Color(0xFFF0ECE4)
    val Gold = Color(0xFFC19A5B)
    val GoldDark = Color(0xFF8B642F)

    val Ink = Color(0xFF292421)
    val InkMuted = Color(0xFF716861)
    val InkFaint = Color(0xFF8C827A)
    val Disabled = Color(0xFFB2AAA2)
    val OnDark = Color(0xFFFFFCF7)

    val BorderStrong = Color(0xFF9D938A)
    val Border = Color(0xFFDDD5CA)
    val BorderSoft = Color(0xFFE9E3DA)
    val Shadow = Color(0x24292421)

    val Error = Color(0xFFB8483F)
    val Warning = Color(0xFFC67A34)
    val Success = Color(0xFF47765A)

    val GrammarPaper = Color(0xFFF0DCD8)
    val GrammarInk = Color(0xFF702822)
    val PhrasePaper = Color(0xFFF3E5CD)
    val PhraseInk = Color(0xFF8B642F)
    val PatternPaper = Color(0xFFE7E8D9)
    val PatternInk = Color(0xFF59603F)
    val WordPaper = Color(0xFFF4E2DE)
    val WordInk = Color(0xFFA33A32)
    val PronunciationPaper = Color(0xFFE9E3DA)
    val PronunciationInk = Color(0xFF716861)
    val NotePaper = Color(0xFFF0E5D1)
    val NoteInk = Color(0xFF8B642F)

    val ScoreExcellentPaper = Color(0xFFE7EBDD)
    val ScoreExcellentBorder = Color(0xFFB9C7AC)
    val ScoreGoodPaper = Color(0xFFF3E5CD)
    val ScoreGoodBorder = Color(0xFFD7BC86)
    val ScoreFairPaper = Color(0xFFF5E4D7)
    val ScoreFairBorder = Color(0xFFD9AA7C)
    val ScoreNeedsWorkBorder = Color(0xFFD89A94)
}

@Immutable
data class WerusKnowledgePalette(
    val container: Color,
    val content: Color,
)

fun KnowledgeType.werusPalette(): WerusKnowledgePalette = when (this) {
    KnowledgeType.GRAMMAR -> WerusKnowledgePalette(WerusColors.GrammarPaper, WerusColors.GrammarInk)
    KnowledgeType.PHRASE -> WerusKnowledgePalette(WerusColors.PhrasePaper, WerusColors.PhraseInk)
    KnowledgeType.PATTERN -> WerusKnowledgePalette(WerusColors.PatternPaper, WerusColors.PatternInk)
    KnowledgeType.WORD -> WerusKnowledgePalette(WerusColors.WordPaper, WerusColors.WordInk)
    KnowledgeType.PRONUNCIATION -> WerusKnowledgePalette(WerusColors.PronunciationPaper, WerusColors.PronunciationInk)
    KnowledgeType.AI_NOTE -> WerusKnowledgePalette(WerusColors.NotePaper, WerusColors.NoteInk)
}
