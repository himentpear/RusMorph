import { streamConversation } from "../ai/conversationService";
import { SCENARIOS, type ConversationRequest, type Turn } from "../types";

type AiRunner = Parameters<typeof streamConversation>[0];

function invalid(message: string, status = 400): Response { return Response.json({ error: message }, { status }); }

export async function conversationRoute(request: Request, ai: AiRunner): Promise<Response> {
  if (request.method !== "POST") return invalid("Method not allowed", 405);
  if (Number(request.headers.get("content-length") || 0) > 16_384) return invalid("Request too large", 413);
  let raw: unknown;
  try { raw = await request.json(); } catch { return invalid("Invalid JSON"); }
  if (!raw || typeof raw !== "object") return invalid("Invalid request");
  const body = raw as Record<string, unknown>;
  if (typeof body.sessionId !== "string" || body.sessionId.length > 100 || !body.sessionId.trim()) return invalid("Invalid sessionId");
  if (typeof body.scenario !== "string" || !SCENARIOS.includes(body.scenario as typeof SCENARIOS[number])) return invalid("Invalid scenario");
  if (typeof body.message !== "string" || !body.message.trim() || body.message.length > 2000) return invalid("Invalid message");
  if (!Array.isArray(body.history) || body.history.length > 10 || !body.history.every((turn: unknown) =>
    turn && typeof turn === "object" && "role" in turn && "content" in turn &&
    (turn.role === "user" || turn.role === "assistant") && typeof turn.content === "string" && turn.content.length <= 2000)) return invalid("Invalid history");
  const input = body as ConversationRequest;
  input.history = input.history as Turn[];
  try { return await streamConversation(ai, input); }
  catch (error) {
    console.error(JSON.stringify({ event: "provider_start_failed", reason: error instanceof Error ? error.message : "unknown" }));
    return invalid("AI provider unavailable", 502);
  }
}
