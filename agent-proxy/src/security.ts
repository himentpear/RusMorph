import type { Env } from "./providers/AgentProvider";
import { HttpError } from "./errors";

export function authorize(request: Request, env: Env): void {
  if (!env.PROXY_ACCESS_TOKEN) return;
  if (request.headers.get("authorization") !== `Bearer ${env.PROXY_ACCESS_TOKEN}`) {
    throw new HttpError(401, "UNAUTHORIZED_PROXY", "Unauthorized proxy request");
  }
}

export interface RateLimiter { check(key: string, now?: number): Promise<boolean>; }
export class MemoryRateLimiter implements RateLimiter {
  private readonly buckets = new Map<string, number[]>();
  constructor(private limit = 20, private windowMs = 60_000) {}
  async check(key: string, now = Date.now()): Promise<boolean> {
    const recent = (this.buckets.get(key) ?? []).filter(value => now - value < this.windowMs);
    if (recent.length >= this.limit) return false;
    recent.push(now); this.buckets.set(key, recent); return true;
  }
}
