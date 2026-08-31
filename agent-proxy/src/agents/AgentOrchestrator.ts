import type { MultiAgentProvider, Env } from "../providers/AgentProvider";
import { RouterAgent, stripFence } from "./RouterAgent";
import { LexiconRetrievalAgent } from "./LexiconRetrievalAgent";
import { LinguisticAnalysisAgent } from "./LinguisticAnalysisAgent";
import { ExampleAgent } from "./ExampleAgent";
import { ComparisonAgent } from "./ComparisonAgent";
import { CardComposerAgent } from "./CardComposerAgent";
import { LocalLibraryAgent } from "./LocalLibraryAgent";
import { commandPlanSchema, orchestrateResponseSchema, type AgentCommandPlan, type LocalEntry, type WordCard } from "./schemas";

export class AgentOrchestrator {
  private router: RouterAgent;
  private retrieval = new LexiconRetrievalAgent();
  private linguistic: LinguisticAnalysisAgent;
  private examples: ExampleAgent;
  private comparison: ComparisonAgent;
  private composer: CardComposerAgent;
  private library = new LocalLibraryAgent();
  constructor(private provider: MultiAgentProvider) {
    this.router = new RouterAgent(provider); this.linguistic = new LinguisticAnalysisAgent(provider);
    this.examples = new ExampleAgent(provider); this.comparison = new ComparisonAgent(provider);
    this.composer = new CardComposerAgent(provider);
  }

  route(command: string, session: unknown, env: Env) { return this.router.route(command, session, env); }

  async execute(command: string, session: unknown, localEntries: LocalEntry[], env: Env, suppliedPlan?: AgentCommandPlan) {
    const plan = suppliedPlan ? commandPlanSchema.parse(suppliedPlan) : await this.route(command, session, env);
    if (plan.outputMode === "CLARIFICATION") return orchestrateResponseSchema.parse({ phase: "CLARIFICATION", plan, clarification: plan.clarification ?? "请补充要操作的词条。", warnings: [] });
    const action = this.library.action(plan, extractArchiveName(command));
    if (action) return orchestrateResponseSchema.parse({ phase: "COMPLETE", plan, localAction: action, warnings: [] });
    if (this.retrieval.validate(plan, localEntries).requiresLocalRetrieval) {
      return orchestrateResponseSchema.parse({ phase: "LOCAL_RETRIEVAL_REQUIRED", plan, warnings: [] });
    }
    if (localEntries.length === 0) return orchestrateResponseSchema.parse({ phase: "CLARIFICATION", plan, clarification: "本地词库没有返回可用词条，请修改查询。", warnings: [{ code: "NO_LOCAL_MATCH", message: "不得由模型伪造数据库命中" }] });

    let cards = localEntries.map(baseCard);
    if (["ETYMOLOGY", "MORPHOLOGY", "LOOKUP"].includes(plan.intent)) {
      const analysis = safeObject(await this.linguistic.complete({ command, localEntries }, env));
      cards = cards.map(card => ({ ...card, morphology: stringOrUndefined(analysis.morphology) ?? card.morphology, etymology: stringOrUndefined(analysis.etymology), warnings: [...card.warnings, ...stringArray(analysis.warnings).map(message => ({ code: "LINGUISTIC_WARNING", message }))], evidence: [...card.evidence, ...(analysis.etymology ? [{ type: "MODEL_KNOWLEDGE" as const, title: "模型通用语言学知识" }] : [])] }));
    }
    if (plan.intent === "EXAMPLE") {
      const example = safeObject(await this.examples.complete({ command, localEntries }, env));
      const generated = Array.isArray(example.sentences) ? example.sentences.filter(isGeneratedSentence) : [];
      cards = cards.map(card => ({ ...card, generatedSentences: generated.map(item => ({ ...item, evidenceType: "MODEL_GENERATED_EXAMPLE" as const })), evidence: [...card.evidence, { type: "MODEL_GENERATED_EXAMPLE" as const, title: "模型生成例句" }] }));
    }
    let comparison;
    if (plan.intent === "COMPARE") {
      const compared = safeObject(await this.comparison.complete({ command, localEntries }, env));
      const distinctions = stringArray(compared.distinctions);
      cards = cards.map(card => ({ ...card, distinctions }));
      comparison = { entryIds: localEntries.map(item => item.entryId), dimensions: { 词义: localEntries.map(item => item.meanings.join("；") || null), 词性: localEntries.map(item => item.partsOfSpeech.join("、") || null) }, evidence: localEntries.map(item => ({ type: "LEXICON_FIELD" as const, title: item.word })) };
    }
    if (plan.outputMode === "WORD_CARD" && cards.length === 1) {
      const card = await this.composer.compose(cards[0], false, env);
      return orchestrateResponseSchema.parse({ phase: "COMPLETE", plan, card, warnings: [] });
    }
    const deckInput = { id: `deck-${crypto.randomUUID()}`, title: deckTitle(plan, command), cards, warnings: [] };
    const deck = await this.composer.compose(deckInput, true, env);
    return orchestrateResponseSchema.parse({ phase: "COMPLETE", plan, deck, comparison, warnings: [] });
  }
}

function baseCard(entry: LocalEntry): WordCard {
  const morphologyFields = [entry.gender, entry.declensionClass, entry.endingType, entry.pluralStressPattern, entry.aspect, entry.conjugationClass, entry.phoneticAlternation].filter(Boolean);
  return {
    id: `card-${entry.entryId}`, entryId: entry.entryId, word: entry.word, normalizedWord: entry.normalizedWord, matchedForm: entry.matchedForm,
    meanings: entry.meanings, partsOfSpeech: entry.partsOfSpeech, stress: entry.word.includes("́") ? entry.word : undefined,
    morphology: morphologyFields.length ? morphologyFields.join("；") : undefined, etymology: undefined,
    generatedSentences: [], sourceExamples: entry.sourceExamples.map(item => ({ ...item, evidenceType: "SOURCE_EXAMPLE" as const })), distinctions: [], commonErrors: [],
    evidence: [{ type: "LEXICON_FIELD" as const, title: "本地词表" }, ...entry.sourceExamples.map(() => ({ type: "SOURCE_EXAMPLE" as const, title: "本地来源例句" }))],
    warnings: [], favorite: false, archiveIds: [], reviewStatus: undefined,
  };
}
function safeObject(text: string): Record<string, unknown> { try { const value = JSON.parse(stripFence(text)); return value && typeof value === "object" ? value : {}; } catch { return {}; } }
function stringOrUndefined(value: unknown): string | undefined { return typeof value === "string" && value.trim() ? value : undefined; }
function stringArray(value: unknown): string[] { return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : []; }
function isGeneratedSentence(value: unknown): value is { russian: string; chinese?: string } { return !!value && typeof value === "object" && typeof (value as { russian?: unknown }).russian === "string"; }
function deckTitle(plan: AgentCommandPlan, command: string): string { return plan.intent === "COMPARE" ? "词语辨析" : plan.intent === "LESSON_DECK" ? "课号词卡" : plan.intent === "PART_OF_SPEECH_DECK" ? "词类词卡" : command.slice(0, 80); }
function extractArchiveName(command: string): string | undefined { return /(?:放到|归档到|删除)\s*([^，。]+?)(?:归档)?$/.exec(command)?.[1]?.trim(); }
