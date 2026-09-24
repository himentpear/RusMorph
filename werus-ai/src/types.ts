export const SCENARIOS = ["free", "cafe", "campus", "shop", "travel"] as const;
export type Scenario = typeof SCENARIOS[number];
export type Turn = { role: "user" | "assistant"; content: string };
export type ConversationRequest = { sessionId: string; scenario: Scenario; message: string; history: Turn[] };
export type Correction = { hasError: boolean; original: string | null; corrected: string | null; explanationZh: string | null };
export const NO_CORRECTION: Correction = { hasError: false, original: null, corrected: null, explanationZh: null };
