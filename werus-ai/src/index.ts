import { conversationRoute } from "./routes/conversation";

export function createHandler(ai: Parameters<typeof conversationRoute>[1]) {
  return async (request: Request): Promise<Response> => {
    const url = new URL(request.url);
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Accept",
    };
    if (url.pathname !== "/api/conversation") return Response.json({ error: "Not found" }, { status: 404, headers: cors });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const response = await conversationRoute(request, ai);
    Object.entries(cors).forEach(([key, value]) => response.headers.set(key, value));
    return response;
  };
}

export default {
  fetch(request: Request, env: Env): Promise<Response> { return createHandler(env.AI)(request); },
};
