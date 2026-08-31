import type { AgentRequest, AgentResponse } from "./schema";

export const SYSTEM_PROMPT = `你是面向中国俄语学习者的俄语形态学与历史语法辅助教师。严格区分本地词表事实、明确关联的本地知识文档与模型通用知识。不得把推断伪装为数据库事实，不得虚构古俄语、古教会斯拉夫语、原始斯拉夫语形式或参考页码。字段为空只表示本地未记录。派生词与变格、变位形式必须区分。默认用中文、保留西里尔字母，输出适合手机阅读的 JSON，并提供 evidence、warnings 与 grounding。`;

export function providerMessages(request: AgentRequest) {
  return [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: JSON.stringify({ question: request.question, word: request.word, entryContext: request.entryContext, knowledgeContext: request.knowledgeContext }) },
  ];
}

export function parseProviderOutput(text: string, request: AgentRequest): AgentResponse {
  const cleaned = text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try {
    const value = JSON.parse(cleaned) as Partial<AgentResponse>;
    if (typeof value.answer !== "string" || !value.answer.trim()) throw new Error("answer missing");
    return {
      requestId: request.requestId, conversationId: request.conversationId, answer: value.answer,
      answerSections: Array.isArray(value.answerSections) ? value.answerSections : [],
      evidence: Array.isArray(value.evidence) ? value.evidence : [], warnings: Array.isArray(value.warnings) ? value.warnings : [],
      grounding: value.grounding ?? { hasLexiconEvidence: true, hasKnowledgeEvidence: request.knowledgeContext.length > 0, usedGeneralModelKnowledge: true },
    };
  } catch {
    return {
      requestId: request.requestId, conversationId: request.conversationId, answer: text,
      answerSections: [], evidence: [], warnings: ["模型未返回结构化格式"],
      grounding: { hasLexiconEvidence: false, hasKnowledgeEvidence: false, usedGeneralModelKnowledge: true },
    };
  }
}
