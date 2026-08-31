ALTER TABLE review_tasks ADD COLUMN lesson_number INTEGER CHECK (lesson_number BETWEEN 1 AND 18);
ALTER TABLE review_tasks ADD COLUMN source_type TEXT NOT NULL DEFAULT 'ai_generated'
  CHECK (source_type IN ('textbook_dialogue', 'ai_generated'));

CREATE INDEX IF NOT EXISTS idx_review_tasks_lesson_created
ON review_tasks(lesson_number, created_at DESC);

PRAGMA optimize;
