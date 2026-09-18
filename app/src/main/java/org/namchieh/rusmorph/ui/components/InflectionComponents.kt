@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.namchieh.rusmorph.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.InflectionData
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.WordCardShape

private val CASE_LABELS = listOf(
    "nom" to ("И. 主格" to "1格"),
    "gen" to ("Р. 生格" to "2格"),
    "dat" to ("Д. 与格" to "3格"),
    "acc" to ("В. 宾格" to "4格"),
    "inst" to ("Т. 工具格" to "5格"),
    "prep" to ("П. 前置格" to "6格"),
)

private data class PersonRow(
    val sLabel: String,
    val sVal: String?,
    val pLabel: String,
    val pVal: String?,
)

@Composable
fun InflectionTableCard(
    inflection: InflectionData?,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (inflection == null) {
        AiInflectionFallbackCard(onAskAi = onAskAi, modifier = modifier)
        return
    }

    when (inflection) {
        is InflectionData.Noun -> NounDeclensionCard(inflection, modifier)
        is InflectionData.Verb -> VerbConjugationCard(inflection, modifier)
        is InflectionData.Adjective -> AdjectiveDeclensionCard(inflection, modifier)
    }
}

@Composable
fun NounDeclensionCard(noun: InflectionData.Noun, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = WordCardShape,
        colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "变格表 (Склонение)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RusMorphColors.TextPrimary,
                )
                noun.partner?.takeIf { it.isNotBlank() }?.let { partner ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RusMorphColors.SurfaceMuted,
                    ) {
                        Text(
                            "对应: $partner",
                            style = MaterialTheme.typography.labelSmall,
                            color = RusMorphColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RusMorphColors.SurfaceMuted)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "格 (Падеж)",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RusMorphColors.TextSecondary,
                )
                Text(
                    "单数 (Ед. ч.)",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RusMorphColors.TextSecondary,
                    textAlign = TextAlign.Start,
                )
                Text(
                    "复数 (Мн. ч.)",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RusMorphColors.TextSecondary,
                    textAlign = TextAlign.Start,
                )
            }

            // Table Rows
            CASE_LABELS.forEachIndexed { index, (key, labels) ->
                val (caseName, shortNum) = labels
                val sgVal = noun.singular[key]?.takeIf { it.isNotBlank() } ?: "—"
                val plVal = noun.plural[key]?.takeIf { it.isNotBlank() } ?: "—"
                val isEven = index % 2 == 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isEven) Color.Transparent else RusMorphColors.SurfaceMuted.copy(alpha = 0.35f))
                        .padding(vertical = 7.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(caseName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(shortNum, style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextTertiary)
                    }
                    Text(
                        sgVal,
                        modifier = Modifier.weight(1.4f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = RusMorphColors.TextPrimary,
                    )
                    Text(
                        plVal,
                        modifier = Modifier.weight(1.4f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = RusMorphColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
fun VerbConjugationCard(verb: InflectionData.Verb, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = WordCardShape,
        colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "变位表 (Спряжение)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RusMorphColors.TextPrimary,
                )
                verb.partner?.takeIf { it.isNotBlank() }?.let { partner ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RusMorphColors.SurfaceMuted,
                    ) {
                        Text(
                            "对应体: $partner",
                            style = MaterialTheme.typography.labelSmall,
                            color = RusMorphColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Present / Future Tense Grid
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (verb.aspect == "完成体") "将来时 (Будущее время)" else "现在时 (Настоящее время)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RusMorphColors.Primary,
                )
                val personRows = listOf(
                    PersonRow("я", verb.presentOrFuture["sg1"], "мы", verb.presentOrFuture["pl1"]),
                    PersonRow("ты", verb.presentOrFuture["sg2"], "вы", verb.presentOrFuture["pl2"]),
                    PersonRow("он / она", verb.presentOrFuture["sg3"], "они", verb.presentOrFuture["pl3"]),
                )
                personRows.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConjugationCell(p.sLabel, p.sVal, Modifier.weight(1f))
                        ConjugationCell(p.pLabel, p.pVal, Modifier.weight(1f))
                    }
                }
            }

            // Past Tense
            val hasPast = verb.past.values.any { it.isNotBlank() }
            if (hasPast) {
                HorizontalDivider(color = RusMorphColors.Divider)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "过去时 (Прошедшее время)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RusMorphColors.Primary,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConjugationCell("阳性 (-л)", verb.past["m"], Modifier.weight(1f))
                        ConjugationCell("阴性 (-ла)", verb.past["f"], Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConjugationCell("中性 (-ло)", verb.past["n"], Modifier.weight(1f))
                        ConjugationCell("复数 (-ли)", verb.past["pl"], Modifier.weight(1f))
                    }
                }
            }

            // Imperative
            val hasImp = verb.imperative.values.any { it.isNotBlank() }
            if (hasImp) {
                HorizontalDivider(color = RusMorphColors.Divider)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "命令式 (Повелительное наклонение)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RusMorphColors.Primary,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConjugationCell("单数 (ты)", verb.imperative["sg"], Modifier.weight(1f))
                        ConjugationCell("复数 (вы)", verb.imperative["pl"], Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConjugationCell(label: String, value: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = RusMorphColors.SurfaceMuted.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextSecondary)
            Text(
                value?.takeIf { it.isNotBlank() } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = RusMorphColors.TextPrimary,
            )
        }
    }
}

@Composable
fun AdjectiveDeclensionCard(adj: InflectionData.Adjective, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = WordCardShape,
        colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "形容词变格与级 (Склонение)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RusMorphColors.TextPrimary,
            )

            // Short forms & Comparative
            val hasShort = adj.shortForms.values.any { it.isNotBlank() }
            val hasComp = !adj.comparative.isNullOrBlank()
            if (hasShort || hasComp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasComp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("比较级:", style = MaterialTheme.typography.labelMedium, color = RusMorphColors.TextSecondary)
                            Text(adj.comparative.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = RusMorphColors.Primary)
                        }
                    }
                    if (hasShort) {
                        Text("短尾形式 (Краткая форма):", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShortFormPill("阳", adj.shortForms["m"], Modifier.weight(1f))
                            ShortFormPill("阴", adj.shortForms["f"], Modifier.weight(1f))
                            ShortFormPill("中", adj.shortForms["n"], Modifier.weight(1f))
                            ShortFormPill("复", adj.shortForms["pl"], Modifier.weight(1f))
                        }
                    }
                }
                HorizontalDivider(color = RusMorphColors.Divider)
            }

            // Case declension table
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RusMorphColors.SurfaceMuted)
                    .padding(vertical = 7.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("格", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("阳/中性", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("阴性", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("复数", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            CASE_LABELS.forEachIndexed { index, (key, labels) ->
                val (caseName, _) = labels
                val mVal = adj.cases["m"]?.get(key)?.takeIf { it.isNotBlank() } ?: "—"
                val fVal = adj.cases["f"]?.get(key)?.takeIf { it.isNotBlank() } ?: "—"
                val plVal = adj.cases["pl"]?.get(key)?.takeIf { it.isNotBlank() } ?: "—"
                val isEven = index % 2 == 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isEven) Color.Transparent else RusMorphColors.SurfaceMuted.copy(alpha = 0.35f))
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(caseName.take(2), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Text(mVal, modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                    Text(fVal, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                    Text(plVal, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ShortFormPill(label: String, value: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = RusMorphColors.SurfaceMuted.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextTertiary)
            Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AiInflectionFallbackCard(onAskAi: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = WordCardShape,
        colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "变格 / 变位表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "本地未收录规则形态，可一键通过 AI 获取",
                    style = MaterialTheme.typography.bodySmall,
                    color = RusMorphColors.TextSecondary,
                )
            }
            OutlinedButton(onClick = onAskAi) {
                Text("✦ AI 解析")
            }
        }
    }
}
