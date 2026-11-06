/**
 * 增量同步：拉（pull）与推（push）。
 *
 * 拉取用服务器单调序号做游标，不依赖设备时钟；推送按字段组 LWW 合并，
 * 谁的时间戳更新谁说了算，被覆盖的一方拿回服务器版本自行收敛。
 */
import { currentSeq, tx } from './db.js';
import {
  rowToNote,
  rowToDailyLog,
  mergeNote,
  mergeReview,
  mergeDailyLog,
} from './notes.js';

const MAX_LIMIT = 1000;

/** 一次拉取的结果 */
export function pull(db, { since = 0, limit = 500 } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 500, 1), MAX_LIMIT);
  const from = Math.max(Number(since) || 0, 0);

  const notes = db
    .prepare('SELECT * FROM notes WHERE seq > ? ORDER BY seq ASC LIMIT ?')
    .all(from, lim);
  const reviews = db
    .prepare('SELECT * FROM reviews WHERE seq > ? ORDER BY seq ASC LIMIT ?')
    .all(from, lim);
  const dailyLogs = db
    .prepare('SELECT * FROM daily_logs WHERE seq > ? ORDER BY seq ASC LIMIT ?')
    .all(from, lim);

  const groups = [notes, reviews, dailyLogs];
  const lastSeqOf = (rows) => (rows.length ? Number(rows[rows.length - 1].seq) : null);

  // 游标：有表被截断时取「被截断表里最靠前的末尾」，没有截断就取最大值。
  // 这样不会跳过任何一张表里还没发出去的行。
  const truncated = groups.filter((rows) => rows.length === lim).map(lastSeqOf);
  const hasMore = truncated.length > 0;
  let seq = from;
  if (hasMore) {
    seq = Math.min(...truncated);
  } else {
    for (const rows of groups) {
      const l = lastSeqOf(rows);
      if (l != null && l > seq) seq = l;
    }
  }

  return {
    seq,
    hasMore,
    notes: notes.map(rowToNote),
    reviews: reviews.map((r) => ({
      uuid: r.uuid,
      noteUuid: r.note_uuid,
      grade: Number(r.grade),
      time: Number(r.time),
      seq: Number(r.seq),
    })),
    dailyLogs: dailyLogs.map(rowToDailyLog),
  };
}

/** 推送本地变更，返回合并结果与被服务器顶回来的行 */
export function push(db, body = {}) {
  const notes = Array.isArray(body.notes) ? body.notes : [];
  const reviews = Array.isArray(body.reviews) ? body.reviews : [];
  const dailyLogs = Array.isArray(body.dailyLogs) ? body.dailyLogs : [];

  const applied = { notes: 0, reviews: 0, dailyLogs: 0 };
  const conflicts = { notes: [], dailyLogs: [] };

  tx(db, () => {
    for (const n of notes) {
      const res = mergeNote(db, n);
      if (res.status === 'applied') applied.notes += 1;
      else if (res.note) conflicts.notes.push(res.note);
    }
    for (const r of reviews) {
      if (mergeReview(db, r)) applied.reviews += 1;
    }
    for (const d of dailyLogs) {
      const res = mergeDailyLog(db, d);
      if (res.status === 'applied') applied.dailyLogs += 1;
      else if (res.log) conflicts.dailyLogs.push(res.log);
    }
  });

  return { seq: currentSeq(db), applied, conflicts };
}
