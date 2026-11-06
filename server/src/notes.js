/**
 * notes 仓储层：CRUD、复习、以及同步时的字段组 LWW 合并。
 *
 * 行在库里是 snake_case，对外一律 camelCase，字段与安卓 Note.java 对齐，
 * 安卓端可以直接用 optLong("nextReview") 之类取值。
 */
import { randomUUID } from 'node:crypto';
import { bumpSeq } from './db.js';
import { apply as srsApply } from './srs.js';

/** 内容组：网页和手机都会写，按 contentTs 比较 */
const CONTENT_FIELDS = ['name', 'definition', 'category', 'createdAt', 'deleted'];
/** SRS 组：只有复习动作会写，按 srsTs 比较 */
const SRS_FIELDS = [
  'mastery',
  'intervalDays',
  'easeFactor',
  'reviewCount',
  'nextReview',
  'lastReview',
  'stars',
];

const NUM = (v, d = 0) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : d;
};

/** 库行 -> 对外对象 */
export function rowToNote(r) {
  return {
    uuid: r.uuid,
    name: r.name,
    definition: r.definition ?? '',
    category: r.category ?? '',
    createdAt: NUM(r.created_at),
    mastery: NUM(r.mastery),
    intervalDays: NUM(r.interval_days),
    easeFactor: NUM(r.ease_factor, 250),
    reviewCount: NUM(r.review_count),
    nextReview: NUM(r.next_review),
    lastReview: NUM(r.last_review),
    stars: NUM(r.stars),
    contentTs: NUM(r.content_ts),
    srsTs: NUM(r.srs_ts),
    deleted: NUM(r.deleted) === 1,
    seq: NUM(r.seq),
  };
}

function normalizeContent(input, base = {}) {
  return {
    name: String(input.name ?? base.name ?? '').trim(),
    definition: String(input.definition ?? base.definition ?? ''),
    category: String(input.category ?? base.category ?? '').trim(),
    createdAt: NUM(input.createdAt ?? base.createdAt, Date.now()),
  };
}

function writeRow(db, n) {
  db.prepare(
    `INSERT INTO notes (uuid, name, definition, category, created_at, mastery, interval_days,
                        ease_factor, review_count, next_review, last_review, stars,
                        content_ts, srs_ts, deleted, seq)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(uuid) DO UPDATE SET
       name = excluded.name, definition = excluded.definition, category = excluded.category,
       created_at = excluded.created_at, mastery = excluded.mastery,
       interval_days = excluded.interval_days, ease_factor = excluded.ease_factor,
       review_count = excluded.review_count, next_review = excluded.next_review,
       last_review = excluded.last_review, stars = excluded.stars,
       content_ts = excluded.content_ts, srs_ts = excluded.srs_ts,
       deleted = excluded.deleted, seq = excluded.seq`
  ).run(
    n.uuid,
    n.name,
    n.definition,
    n.category,
    n.createdAt,
    n.mastery,
    n.intervalDays,
    n.easeFactor,
    n.reviewCount,
    n.nextReview,
    n.lastReview,
    n.stars,
    n.contentTs,
    n.srsTs,
    n.deleted ? 1 : 0,
    n.seq
  );
}

export function getNoteRow(db, uuid) {
  return db.prepare('SELECT * FROM notes WHERE uuid = ?').get(uuid);
}

export function getNote(db, uuid) {
  const r = getNoteRow(db, uuid);
  return r ? rowToNote(r) : null;
}

/** 新建一条（网页/导入用），返回完整对象 */
export function createNote(db, input, now = Date.now()) {
  const content = normalizeContent(input);
  if (!content.name) {
    throw Object.assign(new Error('名称不能为空'), { status: 400 });
  }
  const n = {
    uuid: input.uuid || randomUUID(),
    ...content,
    mastery: 0,
    intervalDays: 0,
    easeFactor: 250,
    reviewCount: 0,
    nextReview: 0,
    lastReview: 0,
    stars: 0,
    contentTs: now,
    srsTs: now,
    deleted: false,
    seq: bumpSeq(db),
  };
  writeRow(db, n);
  return n;
}

/** 批量新建（导入用），一次事务 */
export function createNotes(db, inputs, now = Date.now()) {
  const created = [];
  for (const input of inputs) {
    const content = normalizeContent(input);
    if (!content.name) continue;
    const n = {
      uuid: input.uuid || randomUUID(),
      ...content,
      mastery: 0,
      intervalDays: 0,
      easeFactor: 250,
      reviewCount: 0,
      nextReview: 0,
      lastReview: 0,
      stars: 0,
      contentTs: now,
      srsTs: now,
      deleted: false,
      seq: bumpSeq(db),
    };
    writeRow(db, n);
    created.push(n);
  }
  return created;
}

/** 只改内容组（名称/定义/栏目），SRS 组原样保留 */
export function updateNoteContent(db, uuid, patch, now = Date.now()) {
  const row = getNoteRow(db, uuid);
  if (!row) return null;
  const cur = rowToNote(row);
  const next = {
    ...cur,
    name: patch.name != null ? String(patch.name).trim() : cur.name,
    definition: patch.definition != null ? String(patch.definition) : cur.definition,
    category: patch.category != null ? String(patch.category).trim() : cur.category,
    contentTs: now,
    seq: bumpSeq(db),
  };
  if (!next.name) {
    throw Object.assign(new Error('名称不能为空'), { status: 400 });
  }
  writeRow(db, next);
  return next;
}

/** 软删（墓碑），墓碑也参与同步，客户端据此删除本地行 */
export function deleteNote(db, uuid, now = Date.now()) {
  const row = getNoteRow(db, uuid);
  if (!row) return false;
  const cur = rowToNote(row);
  cur.deleted = true;
  cur.contentTs = now;
  cur.seq = bumpSeq(db);
  writeRow(db, cur);
  return true;
}

/** 复习一次：与安卓 QuizFragment.grade 顺序一致（先加星，再跑算法） */
export function reviewNote(db, uuid, grade, now = Date.now()) {
  const row = getNoteRow(db, uuid);
  if (!row) return null;
  const n = rowToNote(row);
  const g = NUM(grade, 1);
  n.stars += g + 1; // AGAIN=1 星, GOOD=2 星, EASY=3 星
  srsApply(n, g, now);
  n.srsTs = now;
  n.seq = bumpSeq(db);
  writeRow(db, n);

  db.prepare(
    'INSERT OR IGNORE INTO reviews (uuid, note_uuid, grade, time, seq) VALUES (?, ?, ?, ?, ?)'
  ).run(randomUUID(), uuid, g, now, bumpSeq(db));

  return n;
}

/**
 * 整库备份，字段与安卓端 v2 导出保持一致，才能互相导入：
 *   { version: 2, exportedAt, reviewHistoryIncluded: false, notes: [...], dailyLogs: [...] }
 * 不含墓碑（删掉的题不该被备份“复活”），不含复习明细（与安卓一致）。
 */
export function exportBackup(db, now = Date.now()) {
  const notes = db
    .prepare('SELECT * FROM notes WHERE deleted = 0 ORDER BY created_at ASC, uuid ASC')
    .all()
    .map(rowToNote)
    .map((n) => ({
      uuid: n.uuid,
      name: n.name,
      definition: n.definition,
      category: n.category,
      createdAt: n.createdAt,
      mastery: n.mastery,
      intervalDays: n.intervalDays,
      easeFactor: n.easeFactor,
      reviewCount: n.reviewCount,
      nextReview: n.nextReview,
      lastReview: n.lastReview,
      stars: n.stars,
    }));
  const dailyLogs = db
    .prepare('SELECT * FROM daily_logs WHERE deleted = 0 ORDER BY created_at ASC, uuid ASC')
    .all()
    .map(rowToDailyLog)
    .map((d) => ({ uuid: d.uuid, content: d.content, createdAt: d.createdAt }));
  return {
    version: 2,
    exportedAt: new Date(now).toISOString(),
    exportedBy: 'losnote-server',
    reviewHistoryIncluded: false,
    notes,
    dailyLogs,
  };
}

/** 列表 + 搜索 + 栏目筛选（不含墓碑） */
export function listNotes(db, { q = '', category = '', limit = 200, offset = 0 } = {}) {
  const where = ['deleted = 0'];
  const args = [];
  if (category) {
    where.push('category = ?');
    args.push(category);
  }
  if (q) {
    where.push('(name LIKE ? OR definition LIKE ?)');
    args.push(`%${q}%`, `%${q}%`);
  }
  const clause = `WHERE ${where.join(' AND ')}`;
  const total = db.prepare(`SELECT COUNT(*) AS c FROM notes ${clause}`).get(...args).c;
  const rows = db
    .prepare(
      `SELECT * FROM notes ${clause} ORDER BY created_at DESC, uuid LIMIT ? OFFSET ?`
    )
    .all(...args, Math.min(Number(limit) || 200, 1000), Math.max(Number(offset) || 0, 0));
  return { total: Number(total), items: rows.map(rowToNote) };
}

/** 栏目列表：每个栏目的总数与已掌握数 */
export function listCategories(db) {
  return db
    .prepare(
      `SELECT category AS name, COUNT(*) AS total,
              SUM(CASE WHEN mastery = 1 THEN 1 ELSE 0 END) AS mastered
         FROM notes WHERE deleted = 0 AND category <> ''
        GROUP BY category ORDER BY category ASC`
    )
    .all()
    .map((r) => ({ name: r.name, total: Number(r.total), mastered: Number(r.mastered) }));
}

/** 到期需复习的题：next_review <= now 或从未复习 */
export function listDue(db, { category = '', limit = 100, onlyUnknown = false, now = Date.now() } = {}) {
  const where = ["deleted = 0", "(next_review <= ? OR review_count = 0)"];
  const args = [now];
  if (category) {
    where.push('category = ?');
    args.push(category);
  }
  if (onlyUnknown) {
    where.push('mastery = 0');
  }
  const rows = db
    .prepare(
      `SELECT * FROM notes WHERE ${where.join(' AND ')}
        ORDER BY next_review ASC, created_at DESC LIMIT ?`
    )
    .all(...args, Math.min(Number(limit) || 100, 1000));
  return rows.map(rowToNote);
}

export function countDue(db, now = Date.now()) {
  return Number(
    db
      .prepare(
        'SELECT COUNT(*) AS c FROM notes WHERE deleted = 0 AND (next_review <= ? OR review_count = 0)'
      )
      .get(now).c
  );
}

/**
 * 同步合并：按字段组 LWW。
 * 返回 { status: 'applied' | 'conflict', note }
 */
export function mergeNote(db, incoming, now = Date.now()) {
  const uuid = String(incoming.uuid || '');
  if (!uuid) {
    throw Object.assign(new Error('同步行缺少 uuid'), { status: 400 });
  }
  const row = getNoteRow(db, uuid);

  if (!row) {
    // 没见过这条：整行照收，时间戳缺失就用 now
    const n = {
      uuid,
      ...normalizeContent(incoming, { createdAt: now }),
      mastery: NUM(incoming.mastery),
      intervalDays: NUM(incoming.intervalDays),
      easeFactor: NUM(incoming.easeFactor, 250) || 250,
      reviewCount: NUM(incoming.reviewCount),
      nextReview: NUM(incoming.nextReview),
      lastReview: NUM(incoming.lastReview),
      stars: NUM(incoming.stars),
      contentTs: NUM(incoming.contentTs, now),
      srsTs: NUM(incoming.srsTs, now),
      deleted: !!incoming.deleted,
      seq: bumpSeq(db),
    };
    writeRow(db, n);
    return { status: 'applied', note: n };
  }

  const cur = rowToNote(row);
  const inContentTs = NUM(incoming.contentTs);
  const inSrsTs = NUM(incoming.srsTs);
  const merged = { ...cur };
  let changed = false;

  if (inContentTs > cur.contentTs) {
    const content = normalizeContent(incoming, cur);
    Object.assign(merged, content);
    merged.deleted = !!incoming.deleted;
    merged.contentTs = inContentTs;
    changed = true;
  }
  if (inSrsTs > cur.srsTs) {
    merged.mastery = NUM(incoming.mastery, cur.mastery);
    merged.intervalDays = NUM(incoming.intervalDays, cur.intervalDays);
    merged.easeFactor = NUM(incoming.easeFactor, cur.easeFactor);
    merged.reviewCount = NUM(incoming.reviewCount, cur.reviewCount);
    merged.nextReview = NUM(incoming.nextReview, cur.nextReview);
    merged.lastReview = NUM(incoming.lastReview, cur.lastReview);
    merged.stars = NUM(incoming.stars, cur.stars);
    merged.srsTs = inSrsTs;
    changed = true;
  }

  if (!changed) {
    // 服务器版本更新或同版本：把服务器版本回给客户端，让两边收敛
    return { status: 'conflict', note: cur };
  }

  merged.seq = bumpSeq(db);
  writeRow(db, merged);
  return { status: 'applied', note: merged };
}

/** 复习记录幂等写入 */
export function mergeReview(db, incoming) {
  const uuid = String(incoming.uuid || '');
  const noteUuid = String(incoming.noteUuid || '');
  if (!uuid || !noteUuid) return false;
  const exists = db.prepare('SELECT 1 FROM reviews WHERE uuid = ?').get(uuid);
  if (exists) return false;
  db.prepare(
    'INSERT INTO reviews (uuid, note_uuid, grade, time, seq) VALUES (?, ?, ?, ?, ?)'
  ).run(uuid, noteUuid, NUM(incoming.grade), NUM(incoming.time, Date.now()), bumpSeq(db));
  return true;
}

export function rowToDailyLog(r) {
  return {
    uuid: r.uuid,
    content: r.content,
    createdAt: NUM(r.created_at),
    updatedTs: NUM(r.updated_ts),
    deleted: NUM(r.deleted) === 1,
    seq: NUM(r.seq),
  };
}

function writeDailyLog(db, d) {
  db.prepare(
    `INSERT INTO daily_logs (uuid, content, created_at, updated_ts, deleted, seq)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(uuid) DO UPDATE SET
       content = excluded.content, created_at = excluded.created_at,
       updated_ts = excluded.updated_ts, deleted = excluded.deleted, seq = excluded.seq`
  ).run(d.uuid, d.content, d.createdAt, d.updatedTs, d.deleted ? 1 : 0, d.seq);
}

export function listDailyLogs(db, { limit = 100 } = {}) {
  return db
    .prepare('SELECT * FROM daily_logs WHERE deleted = 0 ORDER BY created_at DESC LIMIT ?')
    .all(Math.min(Number(limit) || 100, 1000))
    .map(rowToDailyLog);
}

export function createDailyLog(db, input, now = Date.now()) {
  const d = {
    uuid: input.uuid || randomUUID(),
    content: String(input.content || '').trim(),
    createdAt: NUM(input.createdAt, now),
    updatedTs: NUM(input.updatedTs, now),
    deleted: false,
    seq: bumpSeq(db),
  };
  if (!d.content) {
    throw Object.assign(new Error('内容不能为空'), { status: 400 });
  }
  writeDailyLog(db, d);
  return d;
}

export function deleteDailyLog(db, uuid, now = Date.now()) {
  const row = db.prepare('SELECT * FROM daily_logs WHERE uuid = ?').get(uuid);
  if (!row) return false;
  const d = rowToDailyLog(row);
  d.deleted = true;
  d.updatedTs = now;
  d.seq = bumpSeq(db);
  writeDailyLog(db, d);
  return true;
}

export function mergeDailyLog(db, incoming, now = Date.now()) {
  const uuid = String(incoming.uuid || '');
  if (!uuid) return { status: 'conflict', log: null };
  const row = db.prepare('SELECT * FROM daily_logs WHERE uuid = ?').get(uuid);
  if (!row) {
    const d = {
      uuid,
      content: String(incoming.content ?? ''),
      createdAt: NUM(incoming.createdAt, now),
      updatedTs: NUM(incoming.updatedTs, now),
      deleted: !!incoming.deleted,
      seq: bumpSeq(db),
    };
    writeDailyLog(db, d);
    return { status: 'applied', log: d };
  }
  const cur = rowToDailyLog(row);
  if (NUM(incoming.updatedTs) <= cur.updatedTs) {
    return { status: 'conflict', log: cur };
  }
  const next = {
    ...cur,
    content: String(incoming.content ?? cur.content),
    deleted: !!incoming.deleted,
    updatedTs: NUM(incoming.updatedTs),
    seq: bumpSeq(db),
  };
  writeDailyLog(db, next);
  return { status: 'applied', log: next };
}
