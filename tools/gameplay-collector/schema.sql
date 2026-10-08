CREATE TABLE IF NOT EXISTS samples (
 contributor TEXT NOT NULL,
 id TEXT NOT NULL,
 completed_at INTEGER NOT NULL,
 received_at INTEGER NOT NULL,
 sample TEXT NOT NULL,
 PRIMARY KEY (contributor,id)
);
CREATE INDEX IF NOT EXISTS samples_received ON samples(contributor,received_at);
CREATE INDEX IF NOT EXISTS samples_completed ON samples(completed_at);
CREATE TABLE IF NOT EXISTS publisher_status (
 id INTEGER PRIMARY KEY CHECK(id=1),
 state TEXT NOT NULL
);
