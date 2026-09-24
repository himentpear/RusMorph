# WeRus AI

Independent Cloudflare Worker for short Russian tutor conversations. POST `/api/conversation` returns SSE `token`, `correction`, and `done` events. Android sends at most ten prior turns. There is no stored session data or API token in the APK.

Run `npm install`, `npm test`, `npm run typecheck`, then `npx wrangler deploy` from this directory. Type checking generates Cloudflare binding types locally. Set Android `WERUS_AI_BASE_URL` to the deployed HTTPS origin through a Gradle property or environment variable. Speech recognition and speech synthesis use Android system services.
