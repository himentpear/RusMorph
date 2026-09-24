# v0.006 canonical app baseline

- Canonical repository: `himentpear/RusMorph`
- Canonical local worktree: `C:\rusmorph-v006`
- Canonical production branch: `main`
- Current baseline generation: `v0.006`
- Canonical Android application ID: `org.namchieh.rusmorph`
- Canonical UI: the current `C:\rusmorph-v006` UI
- Top-level navigation: Home, Learning, Dictionary, Review, Profile
- Design system: `app/src/main/java/org/namchieh/rusmorph/ui/design/Werus*`

`new`, `werus-ui-v006`, `refactor/phase1-structure`, `chore/align-phase1-refactor`, and `release/v0.004-prep` are historical only. They may be inspected for isolated reference; they are never authoritative for UI, navigation, architecture, version, or release state. Do not merge them into the canonical branch.

Production releases must be tagged from a commit on `main`. Keep the production application ID stable; the `local` flavor uses the `.preview` suffix. The AI conversation source is retained, but its entry is hidden and its direct route shows an unavailable screen when `WERUS_AI_BASE_URL` is empty. No production AI endpoint is presumed by this baseline.

Known consolidation tails: `TECH_DEBT_NETWORK_CLIENT` (Agent, Speech, Update, Conversation use separate clients), `TECH_DEBT_CONVERSATION_TTS` (the existing speech helper fetches network audio before falling back to system TTS, while Conversation uses system TTS only), and `UI_MIGRATION_TAIL` (some screens still reference older typography and component tokens). These do not authorize layout or behavior changes without separate review.
