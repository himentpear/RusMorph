import type { AgentCommandPlan } from "./schemas";
export class LocalLibraryAgent {
  action(plan: AgentCommandPlan, archiveName?: string) {
    const index = plan.selectedCardIndexes[0];
    if (plan.intent === "SAVE_CARD") return { type: "FAVORITE" as const, cardIndex: index, requiresConfirmation: false };
    if (plan.intent === "ARCHIVE_CARD") return { type: "ARCHIVE" as const, cardIndex: index, archiveName, requiresConfirmation: false };
    if (plan.intent === "SAVE_DECK") return { type: "SAVE_DECK" as const, requiresConfirmation: false };
    if (plan.intent === "DELETE_ARCHIVE") return { type: "DELETE_ARCHIVE" as const, archiveName, requiresConfirmation: true };
    return undefined;
  }
}
