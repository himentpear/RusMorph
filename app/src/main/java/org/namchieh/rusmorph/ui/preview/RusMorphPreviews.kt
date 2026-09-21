package org.namchieh.rusmorph.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.card.*
import org.namchieh.rusmorph.ui.laboratory.LaboratoryBackground
import org.namchieh.rusmorph.ui.home.*
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar
import org.namchieh.rusmorph.ui.sheet.FilterBottomSheet
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

private val PreviewCard = TerminalCardData(
    id = "preview-automobile",
    word = "автомоби́ль",
    meaning = "汽车（Preview 示例数据）",
    partsOfSpeech = listOf("名词", "阳性"),
    lesson = 18,
    morphology = listOf("第二变格", "ь 结尾"),
    analysis = listOf("变格法" to "第二变格", "来源说明" to "仅用于设计预览，不写入数据库"),
    sourceLabels = listOf("Preview fake source"),
)

@Preview(name = "AI Search Hero", showBackground = true, widthDp = 390)
@Composable private fun SearchPreview() = RusMorphTheme { LaboratoryBackground { AiSearchHero("", {}, {}, {}, false, Modifier.padding(16.dp)) } }

@Preview(name = "AI Search Hero With Input", showBackground = true, widthDp = 390)
@Composable private fun SearchInputPreview() = RusMorphTheme { LaboratoryBackground { AiSearchHero("писать", {}, {}, {}, false, Modifier.padding(16.dp)) } }

@Preview(name = "Home Empty Compact", showBackground = true, widthDp = 390, heightDp = 760)
@Preview(name = "Home Empty Expanded", showBackground = true, widthDp = 900, heightDp = 700)
@Composable private fun EmptyHomePreview() = RusMorphTheme {
    LaboratoryBackground {
        Column(Modifier.fillMaxSize()) { BrandHeader({}); Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { AiSearchHero("", {}, {}, {}, false); SuggestionCommandRow({}); EmptyTerminalState() }; RusMorphBottomBar(BottomDestination.Home, {}) }
    }
}

@Preview(name = "Home Single Result", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun SingleHomePreview() = RusMorphTheme {
    LaboratoryBackground { Column(Modifier.fillMaxSize()) { BrandHeader({}); Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { AiSearchHero("автомобиль", {}, {}, {}, false); FlippableWordCard(PreviewCard) }; RusMorphBottomBar(BottomDestination.Home, {}) } }
}

@Preview(name = "Home Card Deck", showBackground = true, widthDp = 420, heightDp = 840)
@Composable private fun DeckHomePreview() = RusMorphTheme {
    LaboratoryBackground { Column(Modifier.fillMaxSize()) { BrandHeader({}); Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { AiSearchHero("第一课的动词", {}, {}, {}, false, Modifier.padding(horizontal = 16.dp)); WordCardCarousel(listOf(PreviewCard, PreviewCard.copy(id = "preview-write", word = "писа́ть"))) }; RusMorphBottomBar(BottomDestination.Home, {}) } }
}

@Preview(name = "Word Card Front", showBackground = true, widthDp = 390)
@Composable private fun FrontPreview() = RusMorphTheme { WordCardFront(PreviewCard, modifier = Modifier.padding(16.dp)) }

@Preview(name = "Word Card Back", showBackground = true, widthDp = 390)
@Composable private fun BackPreview() = RusMorphTheme { WordCardBack(PreviewCard, {}, Modifier.padding(16.dp)) }

@Preview(name = "Flippable Card", showBackground = true, widthDp = 390)
@Composable private fun FlipPreview() = RusMorphTheme { FlippableWordCard(PreviewCard, Modifier.padding(16.dp)) }

@Preview(name = "Card Carousel", showBackground = true, widthDp = 420)
@Composable private fun CarouselPreview() = RusMorphTheme { WordCardCarousel(listOf(PreviewCard, PreviewCard.copy(id = "preview-friend", word = "друг")), modifier = Modifier.padding(vertical = 16.dp)) }

@Preview(name = "Filter Sheet", showBackground = true, widthDp = 390, heightDp = 760)
@Composable private fun FilterPreview() = RusMorphTheme { FilterBottomSheet(listOf(1, 2, 3), listOf("名词", "动词", "形容词"), null, null, {}, { _, _ -> }) }

@Preview(name = "Bottom Navigation", showBackground = true, widthDp = 390)
@Composable private fun BottomBarPreview() = RusMorphTheme { RusMorphBottomBar(BottomDestination.Home, {}) }
