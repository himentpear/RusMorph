# Learning Architecture

## Information architecture

The Android client is now organized around `Course → Lesson → LearningUnit → Activity → Review`.
Its primary destinations are Home, Courses, Dictionary, Review and Profile. Dictionary, AI and
pronunciation are shared capabilities reached from learning content rather than parallel products.

Stable routes exist for courses, lessons, vocabulary, grammar, dialogue, text, words, review and
pronunciation. Screens only expose units backed by real repository content.

## Domain and repositories

- `Course`, `Lesson` and `LearningUnit` form the expandable course catalog. Unit types include the
  six current types plus listening, writing and quiz extension points.
- `GrammarPoint`, `Dialogue`/`DialogueLine`, `TextContent`/`TextParagraph` and `Exercise` are
  structured entities rather than HTML page payloads.
- `CourseRepository` combines the local lesson-tagged lexicon with the existing textbook dialogue
  corpus. The Gradle course-asset task packages that shared corpus for Android and repairs its legacy
  source encoding without creating another hand-maintained copy.
- `LearningRepository` owns progress, history, generic review, mistakes and pronunciation summaries.
  Existing word-card review rows remain in their original tables and are counted through an adapter.
- `PronunciationSession` supplies FREE, SENTENCE, DIALOGUE_LINE and TEXT_SENTENCE contexts while
  continuing to use the existing recorder, HTTPS ASR, intelligibility score, word score and clip APIs.
- `AIContext` covers word, grammar, course, lesson, dialogue, text and review entities. The legacy AI
  request accepts an optional structured learning context and remains compatible when it is absent.

## Database compatibility

Room v6 is an additive migration. It leaves all v5 lexicon, morphology, knowledge, saved-card, deck,
archive, review-attempt and wrong-answer tables unchanged. New tables are `learning_progress`,
`learning_activities`, `generic_review_items`, `mistake_items_v2` and `pronunciation_sessions`.
Course content remains versioned build assets instead of being copied into Room.

## Existing capabilities after migration

- Local Russian/Chinese search, morphology, sources and knowledge explanations remain available.
- Word detail, AI questions, smart commands, cards, decks and follow-up flows retain their routes.
- Search voice input and free/course pronunciation use the existing speech pipeline.
- Teacher calibration remains the HTTPS browser workbench reached from Profile → Settings.
- Worker review audio range playback, 90-day retention, APK download and resumable download are not
  coupled to or changed by the Android information architecture.

## Implemented learning experience

- Time-aware Home dashboard, real review/stat counters and continuation from the latest opened lesson.
- Extensible course list, 18-lesson course detail and dynamic lesson learning paths.
- All 1,929 lesson-tagged vocabulary entries, an in-context mini dictionary, word detail, AI and
  generic review insertion.
- Existing textbook dialogue reading and per-line pronunciation. Role-play is capability-gated.
- Five-destination navigation, warm Modern Slavic Academic theme and reusable Rus components.
- The AI workspace is a hidden negative-one layer above the main interface. Pulling downward at the
  top moves the whole foreground down while the AI layer appears with slower parallax, fade and scale.
  Releasing past the threshold enters AI; releasing early springs the foreground back. There is no
  persistent handle or visible AI menu in the initial state.
- Loading, empty and error states; local content is not blocked by AI or ASR availability.

## Content TODO

- Import verified lesson titles, structured grammar, texts, exercises and Chinese dialogue translations.
- Import speaker identities before enabling dialogue role-play; alternating A/B speakers are not inferred.
- Add licensed standard recordings and paragraph/sentence segmentation when authoritative sources exist.
- Complete content-specific grammar/text/practice screens once those repositories contain real records.
- Add a full mixed-type review runner; the current page aggregates real queues and retains the legacy
  word review entry point.
