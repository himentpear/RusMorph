import { z } from "zod";

const optionalText = z.string().trim().min(1).max(2000).optional().nullable();
const knowledgeSchema = z.object({
  chunkId: z.string().min(1).max(200), title: z.string().min(1).max(500),
  category: z.string().min(1).max(100), content: z.string().min(1).max(12000),
  sourceDocument: z.string().min(1).max(500),
  sectionPath: z.array(z.string().max(500)).max(20).default([]),
}).strict();

export const agentRequestSchema = z.object({
  requestId: z.string().uuid(), conversationId: z.string().uuid(), entryId: z.string().min(1).max(300),
  word: z.string().min(1).max(500), normalizedWord: z.string().min(1).max(500),
  questionType: z.enum(["ETYMOLOGY", "DERIVATION", "MORPHOLOGY", "CUSTOM"]),
  question: z.string().trim().min(1).max(1000),
  entryContext: z.object({
    meaningZh: optionalText, partsOfSpeech: z.array(z.string().min(1).max(100)).max(12).default([]),
    lesson: z.number().int().nonnegative().optional().nullable(), gender: optionalText,
    declensionClass: optionalText, endingType: optionalText, pluralStressPattern: optionalText,
    aspect: optionalText, conjugationClass: optionalText, phoneticAlternation: optionalText,
    searchForms: z.array(z.string().min(1).max(500)).max(12).default([]),
    source: z.object({ sheet: z.string().min(1).max(500), row: z.number().int().positive() }).strict().optional().nullable(),
  }).strict(),
  knowledgeContext: z.array(knowledgeSchema).max(4),
  client: z.object({ appVersion: z.string().max(100), locale: z.string().max(50), platform: z.literal("android") }).strict(),
  learningContext: z.object({
    type: z.enum(["NONE", "WORD", "GRAMMAR", "COURSE", "LESSON", "DIALOGUE", "DIALOGUE_LINE", "TEXT", "TEXT_PARAGRAPH", "TEXT_SENTENCE", "REVIEW"]),
    sourceId: optionalText, courseId: optionalText, lessonId: optionalText,
    label: optionalText, excerpt: optionalText,
  }).strict().optional().nullable(),
}).strict();

export type AgentRequest = z.infer<typeof agentRequestSchema>;
export interface AgentResponse {
  requestId: string; conversationId: string; answer: string;
  answerSections: Array<{ title: string; content: string }>;
  evidence: Array<{ type: string; title: string; field?: string; excerpt?: string; sourceDocument?: string; sectionPath?: string[] }>;
  warnings: string[];
  grounding: { hasLexiconEvidence: boolean; hasKnowledgeEvidence: boolean; usedGeneralModelKnowledge: boolean };
}
export const agentResponseSchema = z.object({
  requestId: z.string(), conversationId: z.string(), answer: z.string().min(1),
  answerSections: z.array(z.object({ title: z.string(), content: z.string() })),
  evidence: z.array(z.object({ type: z.string(), title: z.string(), field: z.string().optional(), excerpt: z.string().optional(), sourceDocument: z.string().optional(), sectionPath: z.array(z.string()).optional() })),
  warnings: z.array(z.string()),
  grounding: z.object({ hasLexiconEvidence: z.boolean(), hasKnowledgeEvidence: z.boolean(), usedGeneralModelKnowledge: z.boolean() }),
});
