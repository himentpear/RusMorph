# Learning Architecture

## Availability and privacy guarantees

Learning schedules, cards, notes, review attempts, wrong answers, progress, activities, and pronunciation session summaries are Room data and remain usable without AI or speech connectivity. Temporary recordings are owned by `SpeechRepository` upload helpers and deleted in `finally`, including HTTP failure and coroutine cancellation.

Pronunciation feedback must be displayed as ASR-based intelligibility evidence unless an API response explicitly reports stronger evidence. Synthetic word clip timings are marked `estimated` with zero timing confidence and must not be presented as real forced-alignment timestamps.

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

## Grammar–TEM4 Knowledge Graph

Grammar learning and TEM4 practice are two entry points into one Room-backed knowledge graph. They do
not own separate question models or duplicate question banks.

```text
GrammarPoint
    ↕ GrammarQuestionCrossRef
Question
    ↓ QuestionAttempt
GrammarMastery
```

- `grammar_points` stores the 16 imported knowledge nodes. Points without linked questions remain
  visible and learnable.
- `questions` stores both immutable-source TEM4 records (`TEM4_REAL`) and validated generated records
  (`AI_VARIANT` / `AI_FREE`). A string primary key supports both source families.
- `grammar_question_cross_ref` normalizes every question–point relationship. Spreadsheet mappings are
  imported as `SOURCE_SPREADSHEET` and `UNVERIFIED`; they are preserved as source evidence and are not
  automatically promoted to verified primary concepts.
- `question_attempts` records learning, practice, simulation and AI-variant evidence. Merely opening a
  grammar page does not change mastery.
- `grammar_mastery` keeps separate real-question and AI-question totals. The initial calculation gives
  real TEM4 accuracy weight 0.7 and AI accuracy weight 0.3, renormalizing when only one evidence family
  exists.
- `question_lineage` connects each generated variant to its source question, target grammar point and
  generation identifier.

Room v7 adds these tables through the additive `MIGRATION_6_7`. Existing lexicon, textbook, word-book,
review and learning tables are neither deleted nor rewritten. Source XLSX is converted at build time to
`grammar_points.json`, `tem4_questions.json` and `grammar_question_links.json`; Android never parses XLSX.

The learner flows are:

- Grammar → linked TEM4 question → shared QuestionRunner → analysis / GrammarPeek / AI variant.
- TEM4 → shared QuestionRunner → linked grammar chips → in-place GrammarPeek → continue question.
- AI variant → the same QuestionRunner and QuestionAttempt store, with a distinct visible source label.

`QuestionRunnerViewModel` and `SavedStateHandle` own the selected option, submitted state, correctness,
analysis visibility and active GrammarPeek. Simulation mode retains the same interface but suppresses
answers, explanations, grammar links and AI actions until a future simulation-results phase.

`Lesson.grammarPointIds` is an empty-by-default extension point for future verified textbook mappings.
No lesson-to-grammar relationship is inferred from keywords in this release.

AI variants reuse `AgentRepository` and the existing Cloudflare gateway at `/v1/grammar-variant`.
The Worker and Android client both validate the strict four-option JSON result and matching target point.
Malformed output is never persisted. Network or provider failure only disables variant generation; local
grammar, TEM4 questions, analysis and GrammarPeek remain available.
