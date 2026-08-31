import { z } from "zod";

const shortText = z.string().max(500);
const longText = z.string().max(4_000);
const nullableShort = shortText.nullable();
export const MAX_DECK_CARDS = 200;
export const DEFAULT_DECK_CARDS = 50;

export const intentSchema = z.enum([
  "LOOKUP_WORD", "LOOKUP_MEANING", "FUZZY_LOOKUP", "LOOKUP_BY_LESSON",
  "LOOKUP_BY_PART_OF_SPEECH", "ETYMOLOGY", "STRESS_EXPLANATION",
  "MORPHOLOGY_EXPLANATION", "GENERATE_SENTENCE", "COMPARE_WORDS",
  "CREATE_WORD_DECK", "SAVE_CARD", "SAVE_DECK", "CREATE_ARCHIVE",
  "ADD_TO_ARCHIVE", "START_REVIEW", "SHOW_WRONG_ANSWERS", "GENERAL_QUESTION",
]);
export const outputModeSchema = z.enum(["WORD_CARD", "CARD_DECK", "PLAIN_ANSWER", "CLARIFICATION", "LOCAL_ACTION_RESULT"]);

export const interpretedQuerySchema = z.object({
  raw: z.string().max(1_000),
  words: z.array(shortText).max(10),
  meaning: nullableShort,
  lessonIds: z.array(z.number().int().positive()).max(10),
  partsOfSpeech: z.array(z.string().max(50)).max(10),
  morphologyFilters: z.object({
    genders: z.array(z.string().max(50)).max(4),
    declensionClasses: z.array(z.string().max(100)).max(10),
    endingTypes: z.array(z.string().max(100)).max(10),
    aspects: z.array(z.string().max(100)).max(10),
    conjugationClasses: z.array(z.string().max(100)).max(10),
    phoneticAlternations: z.array(z.string().max(100)).max(10),
    hasPhoneticAlternation: z.boolean().nullable(),
    hasPluralStressPattern: z.boolean().nullable(),
  }).strict(),
  topic: nullableShort,
  referenceTarget: nullableShort,
}).strict();

export const agentCommandPlanSchema = z.object({
  intent: intentSchema,
  outputMode: outputModeSchema,
  interpretedQuery: interpretedQuerySchema,
  retrieval: z.object({
    fuzzy: z.boolean(),
    limit: z.number().int().min(1).max(MAX_DECK_CARDS),
    requiresLocalSearch: z.boolean(),
  }).strict(),
  agents: z.array(z.enum(["LEXICON_RETRIEVAL", "LINGUISTIC_ANALYSIS", "EXAMPLE", "COMPARISON", "CARD_COMPOSER", "LOCAL_LIBRARY", "GENERAL_COMMAND"])).max(8),
  needsClarification: z.boolean(),
  clarificationQuestion: z.string().max(500).nullable(),
}).strict();
export type AgentCommandPlan = z.infer<typeof agentCommandPlanSchema>;

export const routeRequestSchema = z.object({
  requestId: z.string().min(1).max(100),
  conversationId: z.string().min(1).max(100),
  command: z.string().trim().min(1).max(1_000),
  context: z.object({
    currentEntryId: z.string().max(300).nullable(),
    currentCardIds: z.array(z.string().max(300)).max(MAX_DECK_CARDS),
    currentDeckId: z.string().max(300).nullable(),
    locale: z.string().max(20),
    localSearchMiss: z.boolean().default(false),
    deckLimit: z.number().int().min(1).max(MAX_DECK_CARDS).default(DEFAULT_DECK_CARDS),
  }).strict(),
}).strict();
export const routeResponseSchema = z.object({ requestId: z.string().max(100), plan: agentCommandPlanSchema }).strict();

export const localLexiconResultSchema = z.object({
  entryId: z.string().min(1).max(300),
  displayForm: shortText,
  normalizedWord: shortText,
  meaningZh: z.string().max(1_500).nullable(),
  partsOfSpeech: z.array(z.string().max(100)).max(12),
  lesson: z.number().int().positive().nullable(),
  matchedForm: shortText.nullable(),
  localFields: z.object({
    gender: nullableShort,
    declensionClass: nullableShort,
    endingType: nullableShort,
    pluralStressPattern: nullableShort,
    aspect: nullableShort,
    conjugationClass: nullableShort,
    phoneticAlternation: nullableShort,
  }).strict(),
  annotations: z.record(z.string().max(100), z.array(shortText).max(10)).default({}),
}).strict();
export type LocalLexiconResult = z.infer<typeof localLexiconResultSchema>;

export const knowledgeContextSchema = z.object({
  chunkId: z.string().min(1).max(300),
  title: shortText,
  category: z.string().max(100),
  content: longText,
  sourceDocument: shortText,
}).strict();

export const agentEvidenceSchema = z.object({
  type: z.enum(["LEXICON_FIELD", "LOCAL_KNOWLEDGE", "MODEL_KNOWLEDGE", "MODEL_GENERATED_EXAMPLE", "SOURCE_EXAMPLE"]),
  title: shortText,
  field: z.string().max(100).nullable().default(null),
  excerpt: z.string().max(1_000).nullable().default(null),
  sourceDocument: shortText.nullable().default(null),
}).strict();
export const agentWarningSchema = z.object({ code: z.string().min(1).max(100), message: z.string().min(1).max(1_000) }).strict();

const generatedSentenceSchema = z.object({
  russian: z.string().min(1).max(1_000),
  chinese: z.string().max(1_000).nullable(),
  evidenceType: z.literal("MODEL_GENERATED_EXAMPLE"),
}).strict();
const sourceExampleSchema = z.object({
  russian: z.string().min(1).max(1_000),
  chinese: z.string().max(1_000).nullable(),
  evidenceType: z.literal("SOURCE_EXAMPLE"),
}).strict();

const etymologySchema = z.object({
  summary: z.string().max(2_000).nullable(),
  confidence: z.enum(["LOW", "MEDIUM", "HIGH"]),
  sourceType: z.literal("MODEL_KNOWLEDGE"),
}).strict();

export const wordCardSchema = z.object({
  cardId: z.string().min(1).max(300),
  entryId: z.string().min(1).max(300),
  word: z.object({ display: shortText, normalized: shortText, matchedForm: nullableShort, stressNote: nullableShort }).strict(),
  meanings: z.array(z.object({ textZh: z.string().max(1_500), partOfSpeech: z.string().max(100).nullable() }).strict()).max(20),
  morphology: z.object({
    gender: nullableShort, declensionClass: nullableShort, endingType: nullableShort,
    pluralStressPattern: nullableShort, aspect: nullableShort, conjugationClass: nullableShort,
    phoneticAlternation: nullableShort, explanation: z.string().max(2_000).nullable(),
  }).strict(),
  etymology: etymologySchema.nullable(),
  generatedSentences: z.array(generatedSentenceSchema).max(3),
  sourceExamples: z.array(sourceExampleSchema).max(10),
  distinctions: z.array(z.string().max(1_500)).max(10),
  commonErrors: z.array(z.string().max(1_000)).max(5),
  evidence: z.array(agentEvidenceSchema).max(30),
  warnings: z.array(agentWarningSchema).max(10),
  localState: z.object({ favorite: z.boolean(), archiveIds: z.array(z.string().max(300)).max(20), reviewStatus: z.string().max(100) }).strict(),
}).strict();

export const generatedCardAugmentationSchema = z.object({
  entryId: z.string().min(1).max(300),
  stressNote: nullableShort,
  morphologyExplanation: z.string().max(2_000).nullable(),
  etymology: etymologySchema.nullable(),
  generatedSentences: z.array(generatedSentenceSchema).max(3),
  distinctions: z.array(z.string().max(1_500)).max(10),
  commonErrors: z.array(z.string().max(1_000)).max(5),
  evidence: z.array(agentEvidenceSchema).max(20),
  warnings: z.array(agentWarningSchema).max(10),
}).strict();

export const composeAugmentationResponseSchema = z.object({
  cards: z.array(generatedCardAugmentationSchema).max(MAX_DECK_CARDS),
  plainAnswer: z.string().max(4_000).nullable(),
  thinkingSummary: z.string().max(1_000).nullable().default(null),
  warnings: z.array(agentWarningSchema).max(20),
}).strict();
export type ComposeAugmentationResponse = z.infer<typeof composeAugmentationResponseSchema>;

export const cardDeckSchema = z.object({
  deckId: z.string().min(1).max(300),
  title: shortText,
  description: z.string().max(1_000).nullable(),
  deckType: z.enum(["LESSON", "PART_OF_SPEECH", "COMPARISON", "TOPIC", "FUZZY_RESULTS"]),
  generationRule: z.object({
    lessonIds: z.array(z.number().int().positive()).max(10),
    partsOfSpeech: z.array(z.string().max(100)).max(10),
    topic: nullableShort,
    comparedEntryIds: z.array(z.string().max(300)).max(20),
  }).strict(),
  cardIds: z.array(z.string().max(300)).max(MAX_DECK_CARDS),
  comparison: z.object({ entryIds: z.array(z.string().max(300)).min(2).max(20), summary: z.string().max(3_000) }).strict().nullable(),
  localState: z.object({ favorite: z.boolean(), archiveId: z.string().max(300).nullable(), saved: z.boolean() }).strict(),
}).strict();

export const composeRequestSchema = z.object({
  requestId: z.string().min(1).max(100),
  conversationId: z.string().min(1).max(100),
  command: z.string().trim().min(1).max(1_000),
  plan: agentCommandPlanSchema,
  localResults: z.array(localLexiconResultSchema).max(MAX_DECK_CARDS),
  knowledgeContext: z.array(knowledgeContextSchema).max(4),
}).strict();

export const composeModelResponseSchema = z.object({
  outputMode: outputModeSchema,
  cards: z.array(wordCardSchema).max(MAX_DECK_CARDS),
  deck: cardDeckSchema.nullable(),
  plainAnswer: z.string().max(4_000).nullable(),
  thinkingSummary: z.string().max(1_000).nullable(),
  clarification: z.string().max(500).nullable(),
  warnings: z.array(agentWarningSchema).max(20),
}).strict();
export const composeResponseSchema = composeModelResponseSchema.extend({
  requestId: z.string().max(100),
  grounding: z.object({ usedLocalLexicon: z.boolean(), usedLocalKnowledge: z.boolean(), usedModelKnowledge: z.boolean() }).strict(),
}).strict();
export type ComposeModelResponse = z.infer<typeof composeModelResponseSchema>;

export const generalCommandResponseSchema = z.object({
  thinkingSummary: z.string().min(1).max(1_000),
  reply: z.string().min(1).max(4_000),
  warnings: z.array(agentWarningSchema).max(10),
}).strict();
