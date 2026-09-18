# Learning Architecture

## Information architecture

The Android client is now organized around `WordBook → Lesson → LearningUnit → Activity → Review`.
Its primary destinations are Home, Word Books, Dictionary, Review and Profile. Dictionary, AI and
pronunciation are shared capabilities reached from learning content rather than parallel products.

Stable routes exist for word books, lessons, vocabulary, grammar, dialogue, text, words, review and
pronunciation. Screens only expose units backed by real repository content.

## Domain and repositories

- `WordBook`, `Lesson` and `LearningUnit` form the expandable word-book catalog. Unit types include the
  six current types plus listening, writing and quiz extension points.
- `GrammarPoint`, `Dialogue`/`DialogueLine`, `TextContent`/`TextParagraph` and `Exercise` are
  structured entities rather than HTML page payloads.
- `WordBookRepository` exposes independent per-lesson word, dialogue and text boundaries. Its built-in
  implementation combines the local lesson-tagged lexicon with the existing textbook dialogue
  corpus. The Gradle course-asset task packages that shared corpus for Android and repairs its legacy
  source encoding without creating another hand-maintained copy.
- `LearningRepository` owns progress, history, generic review, mistakes and pronunciation summaries.
  Existing word-card review rows remain in their original tables and are counted through an adapter.
- `PronunciationSession` supplies FREE, SENTENCE, DIALOGUE_LINE and TEXT_SENTENCE contexts while
  continuing to use the existing recorder, HTTPS ASR, intelligibility score, word score and clip APIs.
- `AIContext` covers word, grammar, course, lesson, dialogue, text and review entities. The legacy AI
  request accepts an optional structured learning context and remains compatible when it is absent.

## Database compatibility

Room v7 is an additive migration. It leaves all v5 lexicon, morphology, knowledge, saved-card, deck,
archive, review-attempt and wrong-answer tables unchanged. New tables are `learning_progress`,
`learning_activities`, `generic_review_items`, `mistake_items_v2` and `pronunciation_sessions`.
Built-in word-book content remains versioned build assets. The v7 word-book, lesson, membership,
dialogue and text tables reserve persistent storage for imported/custom books.

## Existing capabilities after migration

- Local Russian/Chinese search, morphology, sources and knowledge explanations remain available.
- Word detail, AI questions, smart commands, cards, decks and follow-up flows retain their routes.
- Search voice input and free/course pronunciation use the existing speech pipeline.
- Teacher calibration remains the HTTPS browser workbench reached from Profile → Settings.
- Worker review audio range playback, 90-day retention, APK download and resumable download are not
  coupled to or changed by the Android information architecture.

## Implemented learning experience

- Time-aware Home dashboard, real review/stat counters and continuation from the latest opened lesson.
- Extensible word-book list, 18-lesson book detail and dynamic lesson learning paths.
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
- Add verified grammar/practice content; the text reader already consumes repository paragraphs.
- Add a full mixed-type review runner; the current page aggregates real queues and retains the legacy
  word review entry point.

## Word-book architecture (local v7)

- `WordBookRepository` exposes book and lesson lookup plus independent words/dialogues/texts lists.
  `CourseRepository` remains a deprecated adapter. The old `course/{courseId}` route redirects
  to book details; new content routes include both book and lesson IDs and the actual content ID.
- `database/wordbooks.json` is the offline catalog: 18 lessons, 1,929 lexicon references,
  17 verified dialogue records, zero texts. No dialogue IDs, speakers, translations, or texts
  are inferred from lesson numbers. The supplied cover is bundled as a drawable.
- `AssetWordBookDataSource` resolves explicit lexicon references, preserving order and key flags.
  `RoomWordBookDataSource` reads the same contract. The composite selects one source for each
  book: highest version wins, with assets winning ties. Superseded content never leaks through.
- Room v6 → v7 is additive. The migration preserves learning, favorites and review tables,
  registers a version-zero built-in book, creates its 18 lessons and backfills old `lesson`
  associations. Before the UI becomes ready, the asset importer validates references and
  installs the complete catalog transactionally, including dialogues. Text tables stay empty.
  A failed import can be retried; a successful book version is not imported twice. Higher
  local versions are preserved. Replacement only clears that book's content, never learning data.
- Lesson, dialogue, line, text and paragraph primary keys include book and lesson scope.
  Legacy lexicon `lesson` and learning database `courseId` columns remain for compatibility;
  new domain learning records expose `wordBookId`. Reopening a lesson preserves its progress.
  Book cards aggregate only that book's current lessons; new review/pronunciation identities
  include book/lesson scope. Missing modules display `尚未导入` and have no generated content.
- `RusMorphApi` reserves seven read-only endpoints under `/v1/wordbooks`. List envelopes carry
  `schemaVersion`, contextual IDs, `items`, and `nextCursor`. These contracts are not connected
  to offline initialization; no backend deployment or import/write API is required.

## Validation and release gate

Run `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest`.
On Windows with a non-ASCII checkout path, use an ASCII junction to the same checkout for
forked JVM tests (see README). New tests cover migration record preservation and membership
backfill, catalog counts, same-ID isolation, version precedence, progress retention, empty
API responses and Compose empty/content selection states.

Production upgrade tests remain a release gate: install the existing production APK,
create favorites/reviews/progress, then upgrade with a build signed by the
same key and verify all records plus offline content. Building an APK does not validate
an installed upgrade. Do not publish or increase the release version before this and a
small grayscale rollout. This working branch also requires integration with newer `main`
changes before production release; this change does not merge or deploy them.

Verified on 2026-09-11: 55 JVM tests passed (zero failures/skips), including Room migrations
and the word-book repository/API regressions. Two Compose tests passed on an Android 16
API 36 emulator, including a rerun against the final Debug APK. Debug, unsigned Release,
and instrumentation APK builds succeeded. With emulator Wi-Fi and mobile data disabled,
the actual app opened Home → built-in book → lesson 1 → vocabulary and dialogue; the
empty text module remained unavailable. This smoke test caught a Gson/Kotlin default-value
issue, now covered by a regression assertion and fixed by explicit asset DTO mapping.
No production signed upgrade, production branch integration, grayscale rollout or release
publication was performed. The emulator used a temporary read-only instance.
