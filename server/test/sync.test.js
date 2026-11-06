import test from 'node:test';
import assert from 'node:assert/strict';

import { openDb, currentSeq } from '../src/db.js';
import {
  createNote,
  getNote,
  reviewNote,
  deleteNote,
  mergeNote,
  listCategories,
  listDue,
  createDailyLog,
} from '../src/notes.js';
import { pull, push } from '../src/sync.js';

const fresh = () => openDb(':memory:');

test('新建题目后能拉到，重复拉取为空', () => {
  const db = fresh();
  const n = createNote(db, { name: '闭包', definition: '函数+环境', category: 'JS' });
  const first = pull(db, { since: 0 });
  assert.equal(first.notes.length, 1);
  assert.equal(first.notes[0].uuid, n.uuid);
  assert.equal(first.hasMore, false);

  const second = pull(db, { since: first.seq });
  assert.equal(second.notes.length, 0);
  assert.equal(second.reviews.length, 0);
});

test('推送旧内容 -> 冲突，服务器版本原样返回', () => {
  const db = fresh();
  const n = createNote(db, { name: '闭包', category: 'JS' });
  const res = push(db, {
    notes: [{ ...n, name: '旧标题', contentTs: n.contentTs - 5000 }],
  });
  assert.equal(res.applied.notes, 0);
  assert.equal(res.conflicts.notes.length, 1);
  assert.equal(res.conflicts.notes[0].name, '闭包');
  assert.equal(getNote(db, n.uuid).name, '闭包');
});

test('推送新内容 -> 覆盖内容组，SRS 组保留', () => {
  const db = fresh();
  const n = createNote(db, { name: '闭包', category: 'JS' });
  const reviewed = reviewNote(db, n.uuid, 2, 1000); // 先复习，SRS 有了值
  const res = push(db, {
    notes: [
      {
        ...reviewed,
        name: '闭包（改）',
        definition: '新定义',
        contentTs: reviewed.contentTs + 5000,
        // 故意把 SRS 时间戳往旧里带，模拟网页端不知道复习状态
        srsTs: 1,
        stars: 0,
      },
    ],
  });
  assert.equal(res.applied.notes, 1);
  const got = getNote(db, n.uuid);
  assert.equal(got.name, '闭包（改）');
  assert.equal(got.definition, '新定义');
  assert.equal(got.srsTs, reviewed.srsTs, 'SRS 时间戳不变');
  assert.equal(got.stars, reviewed.stars, 'SRS 字段不被内容推送冲掉');
  assert.equal(got.reviewCount, 1);
});

test('复习会写 SRS 组并追加复习记录', () => {
  const db = fresh();
  const n = createNote(db, { name: 'TCP', category: '网络' });
  const reviewed = reviewNote(db, n.uuid, 1, 5000);
  assert.equal(reviewed.reviewCount, 1);
  assert.equal(reviewed.stars, 2); // GOOD = 2 星
  assert.equal(reviewed.mastery, 1);

  const got = pull(db, { since: 0 });
  assert.equal(got.reviews.length, 1);
  assert.equal(got.reviews[0].noteUuid, n.uuid);
  assert.equal(got.reviews[0].grade, 1);
});

test('复习记录重复推送只记一次', () => {
  const db = fresh();
  const n = createNote(db, { name: 'X', category: 'A' });
  const review = { uuid: 'rv-1', noteUuid: n.uuid, grade: 1, time: 9000 };
  push(db, { reviews: [review] });
  push(db, { reviews: [review] });
  const got = pull(db, { since: 0 });
  assert.equal(got.reviews.length, 1);
});

test('软删以墓碑形式同步出去', () => {
  const db = fresh();
  const n = createNote(db, { name: '要删的', category: 'A' });
  const seqBefore = currentSeq(db);
  deleteNote(db, n.uuid);
  const changes = pull(db, { since: seqBefore });
  assert.equal(changes.notes.length, 1);
  assert.equal(changes.notes[0].deleted, true);
});

test('客户端首次全量上传（含 SRS）能被服务器接住', () => {
  const db = fresh();
  const now = 1_700_000_000_000;
  const incoming = {
    uuid: 'local-1',
    name: '手机上的老题',
    definition: '带着复习进度',
    category: '旧',
    createdAt: now - 100000,
    mastery: 1,
    intervalDays: 12,
    easeFactor: 275,
    reviewCount: 6,
    nextReview: now + 86400000,
    lastReview: now - 1000,
    stars: 15,
    contentTs: now - 5000,
    srsTs: now - 1000,
    deleted: false,
  };
  const res = push(db, { notes: [incoming] });
  assert.equal(res.applied.notes, 1);
  const got = getNote(db, 'local-1');
  assert.equal(got.intervalDays, 12);
  assert.equal(got.easeFactor, 275);
  assert.equal(got.reviewCount, 6);
  assert.equal(got.stars, 15);
  assert.equal(got.mastery, 1);
});

test('栏目统计只算未删除的题', () => {
  const db = fresh();
  createNote(db, { name: 'a', category: 'JS' });
  const b = createNote(db, { name: 'b', category: 'JS' });
  createNote(db, { name: 'c', category: '网络' });
  deleteNote(db, b.uuid);
  const cats = listCategories(db);
  const js = cats.find((c) => c.name === 'JS');
  assert.equal(js.total, 1);
});

test('到期查询：新题都算到期', () => {
  const db = fresh();
  createNote(db, { name: 'a', category: 'JS' });
  const due = listDue(db, { now: Date.now() });
  assert.equal(due.length, 1);
});

test('分页拉取不跳过任何表', () => {
  const db = fresh();
  for (let i = 0; i < 5; i += 1) createNote(db, { name: `n${i}`, category: 'A' });
  let since = 0;
  let rounds = 0;
  let total = 0;
  for (;;) {
    const page = pull(db, { since, limit: 2 });
    total += page.notes.length;
    since = page.seq;
    rounds += 1;
    if (!page.hasMore) break;
    assert.ok(rounds < 20, '分页没有收敛');
  }
  assert.equal(total, 5);
});

test('删除用墓碑推到服务器，新设备拉得到 deleted 标记', () => {
  const db = fresh();
  const n = createNote(db, { name: '待删', category: 'JS' });
  const ts = Date.now() + 1000;
  const res = push(db, { notes: [{ uuid: n.uuid, deleted: true, contentTs: ts, srsTs: ts }] });
  assert.equal(res.applied.notes, 1);
  assert.equal(getNote(db, n.uuid).deleted, true);

  const page = pull(db, { since: 0 });
  const row = page.notes.find((x) => x.uuid === n.uuid);
  assert.ok(row, '墓碑应该出现在拉取结果里');
  assert.equal(row.deleted, true);
});

test('墓碑时间戳比服务器内容旧时不生效', () => {
  const db = fresh();
  const n = createNote(db, { name: '还活着', category: 'JS' });
  const res = push(db, { notes: [{ uuid: n.uuid, deleted: true, contentTs: 1, srsTs: 1 }] });
  assert.equal(res.applied.notes, 0);
  assert.equal(res.conflicts.notes.length, 1);
  assert.equal(getNote(db, n.uuid).deleted, false);
});

test('足迹删除也能同步', () => {
  const db = fresh();
  const d = createDailyLog(db, { content: '读了 20 页' });
  const res = push(db, { dailyLogs: [{ uuid: d.uuid, deleted: true, updatedTs: Date.now() + 1000 }] });
  assert.equal(res.applied.dailyLogs, 1);
  const page = pull(db, { since: 0 });
  const row = page.dailyLogs.find((x) => x.uuid === d.uuid);
  assert.equal(row.deleted, true);
});
