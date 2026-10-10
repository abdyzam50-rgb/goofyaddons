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
CREATE TABLE IF NOT EXISTS ah_daily (
 item TEXT NOT NULL, source TEXT NOT NULL, day INTEGER NOT NULL,
 n INTEGER NOT NULL, sum_price REAL NOT NULL, min_price REAL NOT NULL,
 max_price REAL NOT NULL, last_price REAL NOT NULL, volume REAL,
 last_at INTEGER NOT NULL, last_bucket INTEGER NOT NULL,
 PRIMARY KEY(item,source,day)
);
CREATE INDEX IF NOT EXISTS ah_daily_day ON ah_daily(day);
CREATE TABLE IF NOT EXISTS ah_state (name TEXT PRIMARY KEY,state TEXT NOT NULL);
