export class HttpError extends Error {
  constructor(public status: number, public code: string, message: string, public retryable: boolean = false) { super(message); }
}
export const errorResponse = (error: HttpError, requestId: string) =>
  Response.json({ error: { code: error.code, message: error.message, requestId, retryable: error.retryable } }, { status: error.status });
