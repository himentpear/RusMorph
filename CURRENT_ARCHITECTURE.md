# Current Architecture (before learning-platform refactor)

## Production hardening status

Android production traffic is constrained to the public Cloudflare gateway (`https://api.namchieh.org/`). Local debug is the only variant permitted to use HTTP. Release signing reads only `RUSMORPH_RELEASE_STORE_FILE`, `RUSMORPH_RELEASE_STORE_PASSWORD`, `RUSMORPH_RELEASE_KEY_ALIAS`, and `RUSMORPH_RELEASE_KEY_PASSWORD`; there is no debug-keystore fallback.

With `ENVIRONMENT=production`, the Worker fails closed before paid AI, ASR, or review-login work when a native rate-limit binding is absent. Review sessions are hashed server-side, cookies are `HttpOnly; Secure; SameSite=Strict`, reviewers are owner-isolated, and only explicitly configured administrators can read global statistics.

Workers AI pronunciation responses are labelled `workers_ai_asr_intelligibility_proxy`; they carry provider/model/evidence metadata and never claim forced alignment or phoneme posterior evidence. The optional FastAPI MFA/Praat path is not a public Android endpoint and needs the internal gateway token contract in production.

- Android client: Kotlin, Jetpack Compose, Navigation Compose, ViewModel/StateFlow, Room and Retrofit.
- Root experience: initialization followed by a dictionary/search screen; legacy bottom navigation exposed search, decks, favorites and profile placeholders.
- Local database v5: imported lexicon, search forms, morphology, knowledge chunks, saved cards/decks/archives, word review attempts and wrong answers.
- AI: Android sends bounded word or command context through an HTTPS Cloudflare Worker; local dictionary remains usable when AI is unavailable.
- Speech: a shared recorder calls the HTTPS speech service for ASR, sentence intelligibility scoring, word scores and audio clips. Search voice input and free pronunciation reuse the same repository.
- Teacher review: a separate HTTPS Worker web console provides textbook-dialogue task generation, recording, playback, word annotation, human/machine review, deletion and 90-day audio retention.
- Distribution: the download Worker serves APK files with byte-range support; it is independent from Android navigation.

The stable lexicon, AI, speech, teacher-review and download paths are retained. The learning refactor adds adapters and additive storage around them.

