import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

/**
 * REST 路由集成测试：真起一个服务进程，用 fetch 走一遍鉴权、CRUD、复习、导入、同步。
 * 只验证主路径 + 权限边界，不重复 notes.js 的单元测试。
 */

const SERVER_DIR = resolve(new URL('..', import.meta.url).pathname, 'src');
const TOKEN = 'route-test-token';

let child;
let base;
let dataDir;

async function waitHealthy(url, ms = 15000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    try {
      const r = await fetch(url + '/api/health');
      if (r.ok) return;
    } catch {
      /* 还没起来 */
    }
    await new Promise((r) => setTimeout(r, 120));
  }
  throw new Error('服务在超时内没有就绪');
}

function call(path, { method = 'GET', body, token = TOKEN } = {}) {
  const headers = {};
  if (token) headers.authorization = 'Bearer ' + token;
  if (body !== undefined) headers['content-type'] = 'application/json';
  return fetch(base + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function json(path, opts) {
  const r = await call(path, opts);
  const text = await r.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  return { status: r.status, payload };
}

before(async () => {
  dataDir = mkdtempSync(join(tmpdir(), 'losnote-route-'));
  const port = 20000 + Math.floor(Math.random() * 20000);
  base = 'http://127.0.0.1:' + port;
  child = spawn(process.execPath, [join(SERVER_DIR, 'index.js')], {
    env: {
      ...process.env,
      LOSNOTE_TOKEN: TOKEN,
      LOSNOTE_PORT: String(port),
      LOSNOTE_DB: join(dataDir, 'route.db'),
      // 故意带 `..`：回归「静态目录没归一化导致全部 403」的 bug
      LOSNOTE_STATIC: join(dataDir, 'static', '..', 'static'),
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const staticDir = join(dataDir, 'static');
  mkdirSync(staticDir, { recursive: true });
  writeFileSync(join(staticDir, 'index.html'), '<!doctype html><title>LosNote</title>ok', 'utf8');
  writeFileSync(join(staticDir, 'app.js'), 'console.log(1)', 'utf8');
  child.stderr.on('data', () => {});
  child.stdout.on('data', () => {});
  await waitHealthy(base);
});

after(() => {
  if (child) child.kill('SIGKILL');
  if (dataDir) rmSync(dataDir, { recursive: true, force: true });
});

test('健康检查不需要令牌', async () => {
  const r = await fetch(base + '/api/health');
  assert.equal(r.status, 200);
  const body = await r.json();
  assert.equal(body.ok, true);
});

test('没有令牌或令牌错误一律 401', async () => {
  assert.equal((await call('/api/notes', { token: null })).status, 401);
  assert.equal((await call('/api/notes', { token: 'wrong' })).status, 401);
  assert.equal((await call('/api/notes', { token: '' })).status, 401);
});

test('静态目录带 .. 也能正常托管（回归 403）', async () => {
  const home = await fetch(base + '/');
  assert.equal(home.status, 200);
  assert.match(await home.text(), /LosNote/);

  const asset = await fetch(base + '/app.js');
  assert.equal(asset.status, 200);
  assert.equal(asset.headers.get('content-type').startsWith('text/javascript'), true);

  // 路径穿越必须挡住
  const evil = await fetch(base + '/../package.json');
  assert.ok([403, 404, 200].includes(evil.status));
  assert.equal((await evil.text()).includes('losnote-server'), false);
});

test('新增 → 列表 → 详情 → 搜索 → 栏目筛选', async () => {
  const created = await json('/api/notes', {
    method: 'POST',
    body: { name: '进程与线程的区别', definition: '进程有独立地址空间，线程共享', category: '操作系统' },
  });
  assert.equal(created.status, 200);
  const uuid = created.payload.note.uuid;
  assert.ok(uuid);

  const list = await json('/api/notes');
  assert.equal(list.status, 200);
  assert.ok(list.payload.items.some((n) => n.uuid === uuid));

  const one = await json('/api/notes/' + uuid);
  assert.equal(one.payload.note.name, '进程与线程的区别');

  const searched = await json('/api/notes?q=线程');
  assert.ok(searched.payload.items.some((n) => n.uuid === uuid));
  const notFound = await json('/api/notes?q=不存在的关键词xyz');
  assert.equal(notFound.payload.items.length, 0);

  const byCat = await json('/api/notes?category=' + encodeURIComponent('操作系统'));
  assert.ok(byCat.payload.items.every((n) => n.category === '操作系统'));

  const cats = await json('/api/categories');
  assert.ok(cats.payload.items.some((c) => c.name === '操作系统'));
});

test('修改内容会推进 contentTs，删除后查不到', async () => {
  const created = await json('/api/notes', {
    method: 'POST',
    body: { name: '临时题', definition: 'x', category: '杂项' },
  });
  const before = created.payload.note;

  await new Promise((r) => setTimeout(r, 5));
  const patched = await json('/api/notes/' + before.uuid, {
    method: 'PATCH',
    body: { name: '改过的题', definition: 'y' },
  });
  assert.equal(patched.status, 200);
  assert.equal(patched.payload.note.name, '改过的题');
  assert.ok(patched.payload.note.contentTs > before.contentTs, 'contentTs 应前进');

  const del = await call('/api/notes/' + before.uuid, { method: 'DELETE' });
  assert.equal(del.status, 200);
  assert.equal((await call('/api/notes/' + before.uuid)).status, 404);

  const list = await json('/api/notes');
  assert.ok(!list.payload.items.some((n) => n.uuid === before.uuid));
});

test('复习一次：stars 累加、复习次数 +1、不再算到期', async () => {
  const created = await json('/api/notes', {
    method: 'POST',
    body: { name: 'SRS 题', definition: 'z', category: 'SRS' },
  });
  const uuid = created.payload.note.uuid;

  const dueBefore = await json('/api/quiz/due?limit=50');
  assert.ok(dueBefore.payload.items.some((n) => n.uuid === uuid), '新题应算到期');

  const reviewed = await json('/api/notes/' + uuid + '/review', {
    method: 'POST',
    body: { grade: 2 },
  });
  assert.equal(reviewed.status, 200);
  assert.equal(reviewed.payload.note.stars, 3, 'EASY 给 3 星');
  assert.equal(reviewed.payload.note.reviewCount, 1);
  assert.ok(reviewed.payload.note.nextReview > Date.now());

  const dueAfter = await json('/api/quiz/due?limit=50');
  assert.ok(!dueAfter.payload.items.some((n) => n.uuid === uuid), '复习后应按间隔推后');
});

test('批量解析与提交导入', async () => {
  const blockText = ['@@@', '块题一', '块答案一', '@@@', '块题二', '块答案二'].join('\n');
  const parsed = await json('/api/import/parse', {
    method: 'POST',
    body: { text: blockText, category: '导入' },
  });
  assert.equal(parsed.status, 200);
  assert.equal(parsed.payload.notes.length, 2);
  assert.equal(parsed.payload.notes[0].name, '块题一');

  const pipeText = '行题一 | 行答案一 | 管道栏目\n行题二 | 行答案二';
  const parsed2 = await json('/api/import/parse', {
    method: 'POST',
    body: { text: pipeText, category: '默认栏目' },
  });
  assert.equal(parsed2.payload.notes.length, 2);
  assert.equal(parsed2.payload.notes[1].category, '默认栏目');

  const committed = await json('/api/import/commit', {
    method: 'POST',
    body: { notes: parsed.payload.notes },
  });
  assert.equal(committed.status, 200);
  assert.equal(committed.payload.count, 2);

  const list = await json('/api/notes?category=' + encodeURIComponent('导入'));
  assert.equal(list.payload.items.length, 2);
});

test('整库导出：v2 格式、含足迹、不含墓碑、字段对齐安卓', async () => {
  const made = await json('/api/notes', {
    method: 'POST',
    body: { name: '导出用题', definition: '# 标题\n\n| a | b |', category: '导出' },
  });
  const uuid = made.payload.note.uuid;
  await json('/api/notes/' + uuid + '/review', { method: 'POST', body: { grade: 1 } });
  await json('/api/daily', { method: 'POST', body: { content: '今天背了 20 个单词' } });

  const doomed = await json('/api/notes', {
    method: 'POST',
    body: { name: '导出前删掉的', definition: 'x', category: '导出' },
  });
  await call('/api/notes/' + doomed.payload.note.uuid, { method: 'DELETE' });

  const exp = await json('/api/export');
  assert.equal(exp.status, 200);
  const b = exp.payload;
  assert.equal(b.version, 2);
  assert.equal(b.reviewHistoryIncluded, false);
  assert.ok(b.exportedAt);

  const mine = b.notes.find((n) => n.uuid === uuid);
  assert.ok(mine, '导出里应有自己建的题');
  assert.equal(mine.name, '导出用题');
  assert.equal(mine.reviewCount, 1);
  assert.equal(mine.stars, 2, 'GOOD 给 2 星');
  for (const key of [
    'uuid', 'name', 'definition', 'category', 'createdAt', 'mastery',
    'intervalDays', 'easeFactor', 'reviewCount', 'nextReview', 'lastReview', 'stars',
  ]) {
    assert.ok(key in mine, '缺少安卓 v2 需要的字段: ' + key);
  }
  assert.equal('deleted' in mine, false, '备份里不应出现墓碑标记');

  assert.ok(
    !b.notes.some((n) => n.uuid === doomed.payload.note.uuid),
    '删掉的题不该出现在备份里'
  );
  assert.ok(
    b.dailyLogs.some((d) => d.content === '今天背了 20 个单词'),
    '足迹应进备份'
  );
  const log = b.dailyLogs.find((d) => d.content === '今天背了 20 个单词');
  assert.deepEqual(Object.keys(log).sort(), ['content', 'createdAt', 'uuid'].sort());
});

test('AI 设置：只回 hasKey，不回显密钥', async () => {
  const saved = await json('/api/settings/ai', {
    method: 'PUT',
    body: { provider: 'deepseek', apiKey: 'sk-secret-123', model: 'deepseek-chat' },
  });
  assert.equal(saved.status, 200);

  const got = await json('/api/settings/ai');
  assert.equal(got.status, 200);
  assert.equal(got.payload.hasKey, true);
  assert.equal(JSON.stringify(got.payload).includes('sk-secret-123'), false);
});

test('同步：push 后 pull 拿到游标，增量拉取不重复', async () => {
  const pushed = await json('/api/sync', {
    method: 'POST',
    body: {
      notes: [
        {
          uuid: 'sync-note-1',
          name: '同步题',
          definition: 'd',
          category: '同步',
          createdAt: 1700000000000,
          mastery: 0,
          intervalDays: 0,
          easeFactor: 250,
          reviewCount: 0,
          nextReview: 0,
          lastReview: 0,
          stars: 0,
          contentTs: 1700000000000,
          srsTs: 1700000000000,
        },
      ],
    },
  });
  assert.equal(pushed.status, 200);
  assert.equal(pushed.payload.applied.notes, 1);
  assert.ok(pushed.payload.seq > 0);

  const full = await json('/api/sync?since=0&limit=500');
  assert.ok(full.payload.notes.some((n) => n.uuid === 'sync-note-1'));
  const cursor = full.payload.seq;
  assert.ok(cursor >= pushed.payload.seq);

  const delta = await json('/api/sync?since=' + cursor + '&limit=500');
  assert.equal(delta.payload.notes.length, 0, '同一游标不应再拉到相同数据');
});

test('删除走墓碑，同步能把它带给别的设备', async () => {
  const created = await json('/api/notes', {
    method: 'POST',
    body: { name: '要删的', definition: 'x', category: '墓碑' },
  });
  const uuid = created.payload.note.uuid;
  const cursor = (await json('/api/sync?since=0&limit=1000')).payload.seq;

  await call('/api/notes/' + uuid, { method: 'DELETE' });

  const delta = await json('/api/sync?since=' + cursor + '&limit=1000');
  const tomb = delta.payload.notes.find((n) => n.uuid === uuid);
  assert.ok(tomb, '增量里应有这条删除');
  assert.equal(tomb.deleted, true);
});
