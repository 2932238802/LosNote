/**
 * SQLite 数据库：建表、迁移、全局序号、设置项。
 *
 * 序号 seq 是同步的地基：服务器上任何一行发生写入都会分配一个更大的 seq，
 * 客户端只记「我上次拉到哪」，下次拉 seq > last_seq 的行即可，不依赖设备时钟。
 */
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

/** 表结构。与 docs/sync-design.md 保持一致。 */
const SCHEMA = `
CREATE TABLE IF NOT EXISTS meta (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS notes (
  uuid          TEXT PRIMARY KEY,
  name          TEXT    NOT NULL,
  definition    TEXT    NOT NULL DEFAULT '',
  category      TEXT    NOT NULL DEFAULT '',
  created_at    INTEGER NOT NULL,
  mastery       INTEGER NOT NULL DEFAULT 0,
  interval_days INTEGER NOT NULL DEFAULT 0,
  ease_factor   INTEGER NOT NULL DEFAULT 250,
  review_count  INTEGER NOT NULL DEFAULT 0,
  next_review   INTEGER NOT NULL DEFAULT 0,
  last_review   INTEGER NOT NULL DEFAULT 0,
  stars         INTEGER NOT NULL DEFAULT 0,
  content_ts    INTEGER NOT NULL,
  srs_ts        INTEGER NOT NULL,
  deleted       INTEGER NOT NULL DEFAULT 0,
  seq           INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notes_seq ON notes (seq);
CREATE INDEX IF NOT EXISTS idx_notes_category ON notes (category);
CREATE INDEX IF NOT EXISTS idx_notes_due ON notes (review_count, next_review);

CREATE TABLE IF NOT EXISTS reviews (
  uuid      TEXT PRIMARY KEY,
  note_uuid TEXT    NOT NULL,
  grade     INTEGER NOT NULL,
  time      INTEGER NOT NULL,
  seq       INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reviews_seq ON reviews (seq);
CREATE INDEX IF NOT EXISTS idx_reviews_note ON reviews (note_uuid);

CREATE TABLE IF NOT EXISTS daily_logs (
  uuid       TEXT PRIMARY KEY,
  content    TEXT    NOT NULL,
  created_at INTEGER NOT NULL,
  updated_ts INTEGER NOT NULL,
  deleted    INTEGER NOT NULL DEFAULT 0,
  seq        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_daily_seq ON daily_logs (seq);

CREATE TABLE IF NOT EXISTS settings (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL
);
`;

/**
 * 打开（必要时创建）数据库。
 * @param {string} file 数据库文件路径，`:memory:` 用于测试
 */
export function openDb(file) {
  if (file !== ':memory:') {
    mkdirSync(dirname(file), { recursive: true });
  }
  const db = new DatabaseSync(file);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA synchronous = NORMAL');
  db.exec(SCHEMA);
  return db;
}

/** 当前最大序号 */
export function currentSeq(db) {
  const row = db.prepare("SELECT v FROM meta WHERE k = 'seq'").get();
  return row ? Number(row.v) : 0;
}

/** 分配一个新序号（调用方负责把它写进行里） */
export function bumpSeq(db) {
  const next = currentSeq(db) + 1;
  db.prepare(
    "INSERT INTO meta (k, v) VALUES ('seq', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v"
  ).run(String(next));
  return next;
}

export function getSetting(db, key, fallback = null) {
  const row = db.prepare('SELECT v FROM settings WHERE k = ?').get(key);
  return row ? row.v : fallback;
}

export function setSetting(db, key, value) {
  db.prepare(
    'INSERT INTO settings (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v'
  ).run(key, String(value));
}

/** 把一组写操作放进事务里跑；出错整体回滚 */
export function tx(db, fn) {
  db.exec('BEGIN');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}
