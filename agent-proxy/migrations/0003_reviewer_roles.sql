-- Existing invite holders remain ordinary reviewers.  Administrators are assigned
-- explicitly through REVIEW_ADMIN_NAMES during their next login, never by client UI.
ALTER TABLE reviewers ADD COLUMN role TEXT NOT NULL DEFAULT 'reviewer'
  CHECK (role IN ('reviewer', 'admin'));
