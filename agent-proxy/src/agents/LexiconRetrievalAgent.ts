import type { AgentCommandPlan, LocalEntry } from "./schemas";
export class LexiconRetrievalAgent {
  validate(plan: AgentCommandPlan, entries: LocalEntry[]): { requiresLocalRetrieval: boolean; entries: LocalEntry[] } {
    return { requiresLocalRetrieval: plan.requiresLocalRetrieval && entries.length === 0, entries };
  }
}
