package org.namchieh.rusmorph.ui.screen.learning.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.domain.learning.TextSentence
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography

@Composable
fun SentenceAnalysisPanel(sentence: TextSentence, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(WerusColors.Red))
        Column(
            Modifier.fillMaxWidth().background(WerusColors.BeigeMuted).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnalysisSection("ГРАММАТИКА · 语法", sentence.grammar, "暂无语法标注")
            AnalysisSection("ЗАМЕТКИ · 笔记", sentence.notes, "暂无笔记")
        }
    }
}

@Composable
private fun AnalysisSection(title: String, values: List<String>, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = WerusTypography.Metadata, color = WerusColors.Red)
        if (values.isEmpty()) Text(emptyText, style = WerusTypography.Caption, color = WerusColors.InkFaint)
        else values.forEach { Text(it, style = WerusTypography.Caption, color = WerusColors.InkMuted) }
    }
}
