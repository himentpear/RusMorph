package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.wordcard.model.GrammaticalCase
import org.namchieh.rusmorph.wordcard.model.Lexeme

/**
 * 展开式俄语形态面板 (Morphology Panel)。
 * 完整展示名词六格单复数矩阵、动词人称变位矩阵或形容词短尾与性数配合。
 */
@Composable
fun MorphologyPanel(
    morphology: Lexeme.MorphologyInfo,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = WerusColors.Canvas,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.BorderSoft),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (morphology) {
                    is Lexeme.MorphologyInfo.Noun -> NounMorphologyTable(morphology)
                    is Lexeme.MorphologyInfo.Verb -> VerbMorphologyTable(morphology)
                    is Lexeme.MorphologyInfo.Adjective -> AdjectiveMorphologyTable(morphology)
                    is Lexeme.MorphologyInfo.Generic -> {
                        Text(
                            text = morphology.summaryZh.ifBlank { "暂无完整形态数据" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = WerusColors.InkMuted,
                        )
                    }
                    Lexeme.MorphologyInfo.Empty -> {
                        Text(
                            text = "暂无完整形态数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WerusColors.InkFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NounMorphologyTable(noun: Lexeme.MorphologyInfo.Noun) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("完整六格变格表", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
            if (noun.declensionType.isNotBlank()) {
                Text(noun.declensionType, style = RusMorphTechTypography.MicroPill, color = WerusColors.Info)
            }
        }

        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth().background(WerusColors.BeigeMuted, RoundedCornerShape(6.dp)).padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("格位", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted, modifier = Modifier.weight(1f))
            Text("单数 (ед. ч.)", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted, modifier = Modifier.weight(2f))
            Text("复数 (мн. ч.)", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted, modifier = Modifier.weight(2f))
        }

        val cases = listOf(
            Triple(GrammaticalCase.NOMINATIVE, noun.singular.nominative, noun.plural.nominative),
            Triple(GrammaticalCase.GENITIVE, noun.singular.genitive, noun.plural.genitive),
            Triple(GrammaticalCase.DATIVE, noun.singular.dative, noun.plural.dative),
            Triple(GrammaticalCase.ACCUSATIVE, noun.singular.accusative, noun.plural.accusative),
            Triple(GrammaticalCase.INSTRUMENTAL, noun.singular.instrumental, noun.plural.instrumental),
            Triple(GrammaticalCase.PREPOSITIONAL, noun.singular.prepositional, noun.plural.prepositional),
        )

        cases.forEach { (c, sg, pl) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${c.codeRu} ${c.labelZh}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = WerusColors.InkMuted, modifier = Modifier.weight(1f))
                Text(sg.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, color = WerusColors.Ink, modifier = Modifier.weight(2f))
                Text(pl.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, color = WerusColors.Ink, modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun VerbMorphologyTable(verb: Lexeme.MorphologyInfo.Verb) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("完整动词变位表", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
            val aspectLabel = if (verb.aspect == org.namchieh.rusmorph.wordcard.model.Aspect.PERFECTIVE) "完成体 (СВ)" else "未完成体 (НСВ)"
            Text(aspectLabel, style = RusMorphTechTypography.MicroPill, color = WerusColors.Info)
        }

        verb.aspectPair?.takeIf { it.isNotBlank() }?.let { pair ->
            Text("对应体形式: $pair", style = MaterialTheme.typography.bodySmall, color = WerusColors.InkMuted)
        }

        // Present / Future persons
        val personForms = verb.present ?: verb.future
        val tenseTitle = if (verb.aspect == org.namchieh.rusmorph.wordcard.model.Aspect.PERFECTIVE) "将来时变位" else "现在时变位"

        if (personForms != null && personForms.isPopulated) {
            Text(tenseTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = WerusColors.Ink)
            val rows = listOf(
                Pair("я (1sg)", personForms.firstSingular),
                Pair("ты (2sg)", personForms.secondSingular),
                Pair("он/она (3sg)", personForms.thirdSingular),
                Pair("мы (1pl)", personForms.firstPlural),
                Pair("вы (2pl)", personForms.secondPlural),
                Pair("они (3pl)", personForms.thirdPlural),
            )
            rows.chunked(2).forEach { pairList ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    pairList.forEach { (person, form) ->
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("$person: ", style = MaterialTheme.typography.bodySmall, color = WerusColors.InkMuted)
                            Text(form.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, color = WerusColors.Ink)
                        }
                    }
                }
            }
        }

        // Past forms
        verb.past?.let { past ->
            Text("过去时", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = WerusColors.Ink)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("м. ${past.masculine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("ж. ${past.feminine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("ср. ${past.neuter.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("мн. ${past.plural.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            }
        }

        // Imperative
        verb.imperative?.let { imp ->
            Text("命令式", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = WerusColors.Ink)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("单数: ${imp.singular.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("复数: ${imp.plural.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
private fun AdjectiveMorphologyTable(adj: Lexeme.MorphologyInfo.Adjective) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("形容词性数配合与短尾", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)

        // Agreement Nominative
        Text("主格性数配合", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("阳: ${adj.agreementNominative.masculine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            Text("阴: ${adj.agreementNominative.feminine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            Text("中: ${adj.agreementNominative.neuter.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            Text("复: ${adj.agreementNominative.plural.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
        }

        // Short forms
        adj.shortForms?.let { short ->
            Text("短尾形式", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("阳: ${short.masculine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("阴: ${short.feminine.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("中: ${short.neuter.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                Text("复: ${short.plural.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
            }
        }

        // Comparison
        if (adj.comparative != null || adj.superlative != null) {
            Text("比较级/最高级", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                adj.comparative?.let { Text("比较级: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif) }
                adj.superlative?.let { Text("最高级: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif) }
            }
        }
    }
}
