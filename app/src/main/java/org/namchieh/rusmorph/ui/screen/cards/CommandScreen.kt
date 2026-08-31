package org.namchieh.rusmorph.ui.screen.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.agent.WordCard
import org.namchieh.rusmorph.agent.AgentError
import org.namchieh.rusmorph.ui.CommandUiState
import org.namchieh.rusmorph.ui.CommandViewModel
import org.namchieh.rusmorph.ui.card.*
import org.namchieh.rusmorph.ui.common.*
import org.namchieh.rusmorph.ui.home.AiSearchHero
import org.namchieh.rusmorph.ui.theme.*

@Composable
fun CommandScreen(viewModel: CommandViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val command by viewModel.command.collectAsStateWithLifecycle()
    TerminalBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("←  返回") }
                    Text("词卡助手", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = RusMorphComponentTokens.ContentMaxWidth).align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AiSearchHero(command, viewModel::setCommand, viewModel::submit, onFilter = {}, isLoading = state is CommandUiState.Sending, modifier = Modifier.fillMaxWidth().widthIn(max = RusMorphComponentTokens.SearchMaxWidth).align(Alignment.CenterHorizontally))
                when (val current = state) {
                    CommandUiState.Idle -> Unit
                    CommandUiState.Sending -> TerminalLoadingState("正在理解命令、检索本地词库并整理卡片", Modifier.fillMaxWidth())
                    is CommandUiState.Error -> TerminalMessageState("AI 暂不可用", commandErrorMessage(current.error), Modifier.fillMaxWidth())
                    is CommandUiState.Content -> {
                        current.localMessage?.let { TerminalPanel(Modifier.fillMaxWidth()) { TerminalLabel("LOCAL ACTION"); Text(it, color = RusMorphColors.Primary) } }
                        AiThoughtAndReplyPanel(
                            thinking = current.response.thinkingSummary,
                            reply = current.response.plainAnswer ?: current.response.clarification,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        current.response.card?.let { card -> WordCardView(card, { viewModel.saveCard(card) }, Modifier.align(Alignment.CenterHorizontally).widthIn(max = RusMorphComponentTokens.CardMaxWidth)) }
                        current.response.deck?.let { deck ->
                            TerminalPanel(Modifier.fillMaxWidth()) {
                                TerminalLabel("${deck.cards.size} 张", color = RusMorphColors.Secondary)
                                Text(deck.title, style = MaterialTheme.typography.headlineMedium)
                                RusMorphPrimaryButton("保存卡组", { viewModel.saveDeck(deck) })
                            }
                            WordCardCarousel(deck.cards.map { it.toTerminalCardData() }, onSave = { data -> deck.cards.firstOrNull { it.id == data.id }?.let(viewModel::saveCard) }, modifier = Modifier.fillMaxWidth())
                        }
                        if (current.localMessage?.startsWith("删除归档需要确认") == true) RusMorphPrimaryButton("确认删除归档", viewModel::confirmArchiveDeletion)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiThoughtAndReplyPanel(
    thinking: String?,
    reply: String?,
    modifier: Modifier = Modifier,
) {
    if (thinking.isNullOrBlank() && reply.isNullOrBlank()) return
    TerminalPanel(modifier) {
        thinking?.takeIf(String::isNotBlank)?.let {
            TerminalLabel("AI 思考摘要", color = RusMorphColors.Secondary)
            Text(it, color = RusMorphColors.TextSecondary)
        }
        reply?.takeIf(String::isNotBlank)?.let {
            TerminalLabel("AI 回复", color = RusMorphColors.Primary)
            Text(it, color = RusMorphColors.TextPrimary)
        }
    }
}

@Composable
fun WordCardView(card: WordCard, onSave: () -> Unit, modifier: Modifier = Modifier) {
    FlippableWordCard(card.toTerminalCardData(), modifier.fillMaxWidth(), onSave = onSave)
}

private fun commandErrorMessage(error: AgentError): String = when (error) {
    AgentError.ServiceNotConfigured -> "AI 服务尚未配置，本地查词仍可使用。"
    AgentError.AuthenticationFailed, AgentError.UnauthorizedProxy -> "AI 服务认证失败，请联系维护者。"
    AgentError.BalanceInsufficient -> "AI 服务额度不足，本地查词仍可正常使用。"
    AgentError.RateLimited -> "AI 请求过于频繁，请稍后再试。"
    AgentError.ProviderBusy -> "AI 服务当前繁忙，请稍后重试。"
    AgentError.Timeout -> "AI 回答超时，请稍后重试。"
    AgentError.InvalidResponse -> "AI 返回格式异常，请重新尝试。"
    AgentError.NoNetwork -> "网络不可用，本地查词仍可正常使用。"
    AgentError.ServerError, AgentError.Unknown -> "AI 服务暂时不可用，本地查词仍可正常使用。"
    AgentError.Cancelled -> "请求已取消。"
}
