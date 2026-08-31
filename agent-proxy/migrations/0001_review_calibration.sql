PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS reviewers (
  id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  normalized_name TEXT NOT NULL UNIQUE,
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
  created_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS review_sessions (
  token_hash TEXT PRIMARY KEY,
  reviewer_id TEXT NOT NULL REFERENCES reviewers(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS review_tasks (
  id TEXT PRIMARY KEY,
  created_by TEXT NOT NULL REFERENCES reviewers(id),
  target_text TEXT NOT NULL,
  target_translation TEXT NOT NULL,
  read_prompt_text TEXT NOT NULL,
  read_prompt_translation TEXT NOT NULL,
  difficulty TEXT NOT NULL CHECK (difficulty IN ('beginner', 'intermediate', 'advanced')),
  topic TEXT NOT NULL,
  sample_mode TEXT NOT NULL CHECK (sample_mode IN ('correct', 'minor_error', 'omission', 'substitution', 'off_target', 'noise')),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'submitted', 'archived')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS review_submissions (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL REFERENCES review_tasks(id),
  reviewer_id TEXT NOT NULL REFERENCES reviewers(id),
  audio_key TEXT NOT NULL UNIQUE,
  audio_content_type TEXT NOT NULL,
  audio_bytes INTEGER NOT NULL,
  content_match TEXT NOT NULL CHECK (content_match IN ('exact', 'mostly', 'partial', 'off_target', 'unintelligible')),
  overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
  actual_read_text TEXT,
  word_ratings_json TEXT NOT NULL,
  notes TEXT NOT NULL DEFAULT '',
  machine_result_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_review_sessions_reviewer_expiry
ON review_sessions(reviewer_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_review_tasks_status_created
ON review_tasks(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_tasks_creator_created
ON review_tasks(created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_submissions_reviewer_created
ON review_submissions(reviewer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_submissions_task
ON review_submissions(task_id);

PRAGMA optimize;
