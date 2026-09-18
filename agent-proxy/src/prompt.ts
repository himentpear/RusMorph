import type { AgentRequest, AgentResponse } from "./schema";

export const SYSTEM_PROMPT = `你是面向中国俄语学习者的俄语形态学与历史语法辅助教师。严格区分本地词表事实、明确关联的本地知识文档与模型通用知识。不得把推断伪装为数据库事实，不得虚构古俄语、古教会斯拉夫语、原始斯拉夫语形式或参考页码。字段为空只表示本地未记录。派生词与变格、变位形式必须区分。默认用中文、保留西里尔字母，输出适合手机阅读的 JSON，并提供 evidence、warnings 与 grounding。`;

export function providerMessages(request: AgentRequest) {
  return [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: JSON.stringify({ question: request.question, word: request.word, entryContext: request.entryContext, knowledgeContext: request.knowledgeContext }) },
  ];
}

export function normalizeEvidenceType(value: unknown): string {
  if (typeof value !== "string") return "MODEL_KNOWLEDGE";
  const upper = value.trim().toUpperCase();
  switch (upper) {
    case "LEXICON":
    case "LEXICON_FIELD":
    case "DICTIONARY":
      return "LEXICON_FIELD";
    case "KNOWLEDGE_BASE":
    case "LOCAL_KNOWLEDGE":
    case "KNOWLEDGE":
      return "LOCAL_KNOWLEDGE";
    case "MODEL_GENERATED_EXAMPLE":
    case "GENERATED_EXAMPLE":
      return "MODEL_GENERATED_EXAMPLE";
    case "SOURCE_EXAMPLE":
    case "TEXTBOOK_EXAMPLE":
      return "SOURCE_EXAMPLE";
    case "MODEL_KNOWLEDGE":
    case "MODEL":
    case "GENERAL_KNOWLEDGE":
    default:
      return "MODEL_KNOWLEDGE";
  }
}

export function createAgentFallbackResponse(
  request: AgentRequest,
  warning = "PROVIDER_OUTPUT_INVALID",
): AgentResponse {
  return {
    requestId: request.requestId,
    conversationId: request.conversationId,
    answer: "模型返回格式异常，请重新尝试。",
    answerSections: [],
    evidence: [],
    warnings: [warning],
    grounding: {
      hasLexiconEvidence: false,
      hasKnowledgeEvidence: false,
      usedGeneralModelKnowledge: false,
    },
  };
}

export function parseProviderOutput(text: string, request: AgentRequest): AgentResponse {
  const cleaned = text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  let value: Record<string, unknown>;
  try {
    const parsed = JSON.parse(cleaned);
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
      return createAgentFallbackResponse(request, "PROVIDER_OUTPUT_NOT_OBJECT");
    }
    value = parsed as Record<string, unknown>;
  } catch {
    return createAgentFallbackResponse(request, "PROVIDER_JSON_PARSE_ERROR");
  }

  const rawAnswer = typeof value.answer === "string" && value.answer.trim()
    ? value.answer.trim()
    : typeof value.reply === "string" && value.reply.trim()
      ? value.reply.trim()
      : typeof value.response === "string" && value.response.trim()
        ? value.response.trim()
        : null;

  if (!rawAnswer) {
    return createAgentFallbackResponse(request, "PROVIDER_ANSWER_MISSING");
  }

  const answerSections: Array<{ title: string; content: string }> = Array.isArray(value.answerSections)
    ? value.answerSections.flatMap((sec, idx) => {
        if (typeof sec === "object" && sec !== null && !Array.isArray(sec)) {
          const s = sec as Record<string, unknown>;
          const content = typeof s.content === "string" ? s.content.trim() : "";
          if (!content) return [];
          const title = typeof s.title === "string" && s.title.trim() ? s.title.trim() : `部分 ${idx + 1}`;
          return [{ title, content }];
        }
        if (typeof sec === "string" && sec.trim()) {
          return [{ title: `部分 ${idx + 1}`, content: sec.trim() }];
        }
        return [];
      })
    : [];

  const evidence: Array<{
    type: string;
    title: string;
    field?: string;
    excerpt?: string;
    sourceDocument?: string;
    sectionPath?: string[];
  }> = Array.isArray(value.evidence)
    ? value.evidence.flatMap((ev, idx) => {
        if (typeof ev === "object" && ev !== null && !Array.isArray(ev)) {
          const e = ev as Record<string, unknown>;
          const title = typeof e.title === "string" && e.title.trim() ? e.title.trim() : `依据 ${idx + 1}`;
          const type = normalizeEvidenceType(e.type);
          return [{
            type,
            title,
            field: typeof e.field === "string" && e.field.trim() ? e.field.trim() : undefined,
            excerpt: typeof e.excerpt === "string" && e.excerpt.trim() ? e.excerpt.trim() : undefined,
            sourceDocument: typeof e.sourceDocument === "string" && e.sourceDocument.trim() ? e.sourceDocument.trim() : undefined,
            sectionPath: Array.isArray(e.sectionPath) ? e.sectionPath.map(String).filter(Boolean) : undefined,
          }];
        }
        if (typeof ev === "string" && ev.trim()) {
          return [{
            type: "MODEL_KNOWLEDGE",
            title: `依据 ${idx + 1}`,
            field: undefined,
            excerpt: ev.trim(),
            sourceDocument: undefined,
            sectionPath: undefined,
          }];
        }
        return [];
      })
    : [];

  const warnings: string[] = Array.isArray(value.warnings)
    ? value.warnings.map(w => (typeof w === "string" ? w.trim() : typeof w === "object" && w !== null && "message" in w ? String((w as { message: unknown }).message).trim() : "")).filter(Boolean)
    : [];

  const groundingRaw = (typeof value.grounding === "object" && value.grounding !== null && !Array.isArray(value.grounding))
    ? (value.grounding as Record<string, unknown>)
    : {};

  const grounding = {
    hasLexiconEvidence: groundingRaw.hasLexiconEvidence === true,
    hasKnowledgeEvidence: groundingRaw.hasKnowledgeEvidence === true,
    usedGeneralModelKnowledge: groundingRaw.usedGeneralModelKnowledge === true,
  };

  return {
    requestId: request.requestId,
    conversationId: request.conversationId,
    answer: rawAnswer,
    answerSections,
    evidence,
    warnings,
    grounding,
  };
}
