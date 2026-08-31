import type { MultiAgentProvider, Env, ProviderAgentRequest } from "./AgentProvider";
import type { AgentRequest, AgentResponse } from "../schema";
export class MockProvider implements MultiAgentProvider {
  async ask(request: AgentRequest, _env: Env): Promise<AgentResponse> {
    const fields = Object.entries(request.entryContext).filter(([, v]) => v != null && (!Array.isArray(v) || v.length > 0)).map(([k]) => k);
    return {
      requestId: request.requestId, conversationId: request.conversationId,
      answer: `这是 MockProvider 的确定性联调回答，不是真实语言学结论。已收到词条“${request.word}”，本地字段 ${fields.length} 项，关联知识块 ${request.knowledgeContext.length} 个。`,
      answerSections: [{ title: "联调结果", content: `问题类型：${request.questionType}` }],
      evidence: [{ type: "LEXICON_FIELD", title: "本地词表", excerpt: fields.join(", ") }],
      warnings: ["当前使用 MockProvider，不代表真实 AI 答案"],
      grounding: { hasLexiconEvidence: true, hasKnowledgeEvidence: request.knowledgeContext.length > 0, usedGeneralModelKnowledge: false },
    };
  }
  async complete(request: ProviderAgentRequest, _env: Env): Promise<string> {
    const input = request.input as Record<string, unknown>;
    if (request.task === "ROUTER") return JSON.stringify(mockPlan(String(input.command ?? ""), input.session as Record<string, unknown> | undefined));
    if (request.task === "LINGUISTIC") return JSON.stringify({ morphology: "Mock 形态分析，仅用于联调", etymology: null, warnings: ["Mock 不提供真实词源"] });
    if (request.task === "EXAMPLE") return JSON.stringify({ sentences: [{ russian: "Это тестовый пример.", chinese: "这是测试例句。", evidenceType: "MODEL_GENERATED_EXAMPLE" }] });
    if (request.task === "COMPARISON") return JSON.stringify({ distinctions: ["Mock 辨析，仅用于联调"] });
    if (request.task === "CARD_COMPOSER") return JSON.stringify(input);
    return JSON.stringify({ ok: true, task: request.task });
  }
}

function mockPlan(command: string, session?: Record<string, unknown>) {
  const lesson = /第?([一二三四五六七八九十\d]+)课/.exec(command)?.[1];
  const number = lesson ? ({ 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }[lesson] ?? Number(lesson)) : undefined;
  const pos = ["动词", "名词", "形容词", "副词", "数词", "代词"].find(value => command.includes(value));
  const visibleCards = Array.isArray(session?.visibleCardIds) ? session.visibleCardIds : [];
  if (/收藏这个|归档这个|用它|这个词/.test(command) && !session?.currentEntryId && visibleCards.length === 0) {
    return { intent: "UNKNOWN", outputMode: "CLARIFICATION", queries: [], selectedCardIndexes: [], requiresLocalRetrieval: false, requiresConfirmation: false, clarification: "请先选择一个词条或卡片。" };
  }
  if (/收藏这个/.test(command)) return { intent: "SAVE_CARD", outputMode: "WORD_CARD", queries: [], selectedCardIndexes: [session?.lastSelectedIndex ?? 0], requiresLocalRetrieval: false, requiresConfirmation: false };
  if (/把第?二个.*(?:归档|放到)/.test(command)) return { intent: "ARCHIVE_CARD", outputMode: "WORD_CARD", queries: [], selectedCardIndexes: [1], requiresLocalRetrieval: false, requiresConfirmation: false };
  if (/删除.*归档/.test(command)) return { intent: "DELETE_ARCHIVE", outputMode: "TEXT", queries: [], selectedCardIndexes: [], requiresLocalRetrieval: false, requiresConfirmation: true };
  if (/比较|辨析/.test(command)) return { intent: "COMPARE", outputMode: "COMPARISON", queries: extractTerms(command).map(text => ({ text, maxResults: 5, allowFuzzy: true })), selectedCardIndexes: [], requiresLocalRetrieval: true, requiresConfirmation: false };
  if (/造句|例句/.test(command)) return { intent: "EXAMPLE", outputMode: "WORD_CARD", queries: session?.currentEntryId ? [] : extractTerms(command).map(text => ({ text, maxResults: 5, allowFuzzy: true })), selectedCardIndexes: [], requiresLocalRetrieval: !session?.currentEntryId, requiresConfirmation: false };
  if (/类似|相近|再给.*几个/.test(command)) return { intent: "SIMILAR", outputMode: "CARD_DECK", queries: [], selectedCardIndexes: [], requiresLocalRetrieval: true, requiresConfirmation: false };
  if (number || pos || /一组|一些|几个/.test(command)) return { intent: number ? "LESSON_DECK" : "PART_OF_SPEECH_DECK", outputMode: "CARD_DECK", queries: [{ lesson: number, partOfSpeech: pos, maxResults: 30, allowFuzzy: true }], selectedCardIndexes: [], requiresLocalRetrieval: true, requiresConfirmation: false };
  return { intent: "LOOKUP", outputMode: "WORD_CARD", queries: [{ text: extractTerms(command)[0] ?? command.trim(), maxResults: 10, allowFuzzy: true }], selectedCardIndexes: [], requiresLocalRetrieval: true, requiresConfirmation: false };
}
function extractTerms(command: string): string[] {
  return command.replace(/[，。？！,?!]/g, " ").split(/\s+/).filter(value => value && !/^(比较|辨析|查询|查找|这个词|怎么|造句|和|与)$/.test(value)).slice(0, 4);
}
