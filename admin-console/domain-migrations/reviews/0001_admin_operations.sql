ALTER TABLE review_submissions ADD COLUMN admin_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE review_submissions ADD COLUMN admin_excluded INTEGER NOT NULL DEFAULT 0 CHECK(admin_excluded IN (0,1));
ALTER TABLE review_submissions ADD COLUMN admin_annotation_json TEXT;
CREATE TABLE IF NOT EXISTS admin_audit(operation_id TEXT PRIMARY KEY,actor TEXT NOT NULL,action TEXT NOT NULL,object_id TEXT NOT NULL,fingerprint TEXT NOT NULL,result TEXT NOT NULL,created_at TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS admin_audit_date ON admin_audit(created_at DESC);
CREATE TABLE IF NOT EXISTS admin_guard(passed INTEGER NOT NULL);
CREATE TRIGGER IF NOT EXISTS admin_guard_check BEFORE INSERT ON admin_guard WHEN NEW.passed!=1 BEGIN SELECT RAISE(ABORT,'REVISION_CONFLICT'); END;
