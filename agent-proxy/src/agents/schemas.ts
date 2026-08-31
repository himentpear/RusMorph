import { z } from "zod";

export const outputModeSchema = z.enum(["WORD_CARD", "CARD_DECK", "COMPARISON", "CLARIFICATION", "TEXT"]);
export const intentSchema = z.enum(["LOOKUP", "LESSON_DECK", "PART_OF_SPEECH_DECK", "EXAMPLE", "ETYMOLOGY", "MORPHOLOGY", "COMPARE", "SAVE_CARD", "SAVE_DECK", "ARCHIVE_CARD", "DELETE_ARCHIVE", "SIMILAR", "UNKNOWN"]);
export const lexiconQuerySchema = z.object({ text: z.string().max(300).optional(), lesson: z.number().int().positive().optional(), lessons: z.array(z.number().int().positive()).max(10).default([]), partOfSpeech: z.string().max(50).optional(), maxResults: z.number().int().min(1).max(50).default(20), allowFuzzy: z.boolean().default(true) }).strict();
export const commandPlanSchema = z.object({
  intent: intentSchema, outputMode: outputModeSchema,
  queries: z.array(lexiconQuerySchema).max(10).default([]), selectedCardIndexes: z.array(z.number().int().nonnegative()).max(20).default([]),
  requiresLocalRetrieval: z.boolean(), requiresConfirmation: z.boolean(), clarification: z.string().max(500).optional(),
}).strict();
export type AgentCommandPlan = z.infer<typeof commandPlanSchema>;

export const sessionSchema = z.object({ currentEntryId: z.string().max(300).optional(), currentDeckId: z.string().max(300).optional(), visibleCardIds: z.array(z.string().max(300)).max(50).default([]), lastSelectedIndex: z.number().int().nonnegative().optional(), customQuestion: z.string().max(1000).default("") }).strict();
export const routeRequestSchema = z.object({ command: z.string().trim().min(1).max(1000), session: sessionSchema.default({ visibleCardIds: [], customQuestion: "" }) }).strict();

export const localEntrySchema = z.object({
  entryId: z.string().min(1).max(300), word: z.string().min(1).max(500), normalizedWord: z.string().min(1).max(500), matchedForm: z.string().max(500).optional(),
  meanings: z.array(z.string().max(1000)).max(20).default([]), partsOfSpeech: z.array(z.string().max(100)).max(12).default([]), lesson: z.number().int().positive().optional(),
  gender: z.string().max(100).optional(), declensionClass: z.string().max(100).optional(), endingType: z.string().max(100).optional(), pluralStressPattern: z.string().max(200).optional(), aspect: z.string().max(100).optional(), conjugationClass: z.string().max(100).optional(), phoneticAlternation: z.string().max(200).optional(),
  sourceExamples: z.array(z.object({ russian: z.string().max(1000), chinese: z.string().max(1000).optional() }).strict()).max(20).default([]),
}).strict();

export const evidenceTypeSchema = z.enum(["LEXICON_FIELD", "LOCAL_KNOWLEDGE", "MODEL_KNOWLEDGE", "MODEL_GENERATED_EXAMPLE", "SOURCE_EXAMPLE"]);
export const evidenceSchema = z.object({ type: evidenceTypeSchema, title: z.string().min(1).max(500), field: z.string().max(100).optional(), excerpt: z.string().max(2000).optional(), sourceDocument: z.string().max(500).optional() }).strict();
export const warningSchema = z.object({ code: z.string().min(1).max(100), message: z.string().min(1).max(1000) }).strict();
export const sentenceSchema = z.object({ russian: z.string().min(1).max(1000), chinese: z.string().max(1000).optional(), evidenceType: z.enum(["MODEL_GENERATED_EXAMPLE", "SOURCE_EXAMPLE"]) }).strict();
export const wordCardSchema = z.object({
  id: z.string().min(1).max(300), entryId: z.string().min(1).max(300), word: z.string().min(1).max(500), normalizedWord: z.string().min(1).max(500), matchedForm: z.string().max(500).optional(),
  meanings: z.array(z.string().max(1000)).max(20).default([]), partsOfSpeech: z.array(z.string().max(100)).max(12).default([]), stress: z.string().max(500).optional(), morphology: z.string().max(4000).optional(), etymology: z.string().max(4000).optional(),
  generatedSentences: z.array(sentenceSchema).max(10).default([]), sourceExamples: z.array(sentenceSchema).max(20).default([]), distinctions: z.array(z.string().max(2000)).max(20).default([]), commonErrors: z.array(z.string().max(2000)).max(20).default([]), evidence: z.array(evidenceSchema).max(50).default([]), warnings: z.array(warningSchema).max(20).default([]), favorite: z.boolean().default(false), archiveIds: z.array(z.string().max(300)).max(30).default([]), reviewStatus: z.string().max(100).optional(),
}).strict();
export type WordCard = z.infer<typeof wordCardSchema>;
export const cardDeckSchema = z.object({ id: z.string().min(1).max(300), title: z.string().min(1).max(500), cards: z.array(wordCardSchema).min(1).max(200), warnings: z.array(warningSchema).max(20).default([]) }).strict();
export const comparisonMatrixSchema = z.object({ entryIds: z.array(z.string()).min(2).max(10), dimensions: z.record(z.array(z.string().nullable())), evidence: z.array(evidenceSchema).default([]) }).strict();
export const localActionSchema = z.object({ type: z.enum(["FAVORITE", "ARCHIVE", "SAVE_DECK", "DELETE_ARCHIVE"]), cardId: z.string().optional(), cardIndex: z.number().int().nonnegative().optional(), archiveName: z.string().max(200).optional(), requiresConfirmation: z.boolean() }).strict();

export const orchestrateRequestSchema = z.object({ command: z.string().trim().min(1).max(1000), session: sessionSchema, plan: commandPlanSchema.optional(), localEntries: z.array(localEntrySchema).max(50).default([]) }).strict();
export const orchestrateResponseSchema = z.object({ phase: z.enum(["LOCAL_RETRIEVAL_REQUIRED", "COMPLETE", "CLARIFICATION"]), plan: commandPlanSchema, card: wordCardSchema.optional(), deck: cardDeckSchema.optional(), comparison: comparisonMatrixSchema.optional(), localAction: localActionSchema.optional(), clarification: z.string().optional(), warnings: z.array(warningSchema).default([]) }).strict();
export type LocalEntry = z.infer<typeof localEntrySchema>;
