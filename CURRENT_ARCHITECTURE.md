# Current Architecture (before learning-platform refactor)

- Android client: Kotlin, Jetpack Compose, Navigation Compose, ViewModel/StateFlow, Room and Retrofit.
- Root experience: initialization followed by a dictionary/search screen; legacy bottom navigation exposed search, decks, favorites and profile placeholders.
- Local database v5: imported lexicon, search forms, morphology, knowledge chunks, saved cards/decks/archives, word review attempts and wrong answers.
- AI: Android sends bounded word or command context through an HTTPS Cloudflare Worker; local dictionary remains usable when AI is unavailable.
- Speech: a shared recorder calls the HTTPS speech service for ASR, sentence intelligibility scoring, word scores and audio clips. Search voice input and free pronunciation reuse the same repository.
- Teacher review: a separate HTTPS Worker web console provides textbook-dialogue task generation, recording, playback, word annotation, human/machine review, deletion and 90-day audio retention.
- Distribution: the download Worker serves APK files with byte-range support; it is independent from Android navigation.

The stable lexicon, AI, speech, teacher-review and download paths are retained. The learning refactor adds adapters and additive storage around them.

