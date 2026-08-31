import { route } from "./router";
import type { Env } from "./providers/AgentProvider";

export default { fetch(request: Request, env: Env): Promise<Response> { return route(request, env); } };
