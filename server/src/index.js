/**
 * LosNote 服务端入口：零依赖 HTTP 服务，同时提供 JSON API 和网页端静态文件。
 *
 * 环境变量：
 *   LOSNOTE_TOKEN   访问令牌（必填，没有就不启动）
 *   LOSNOTE_DB      数据库路径，默认 ./data/losnote.db
 *   LOSNOTE_PORT    监听端口，默认 8787
 *   LOSNOTE_STATIC  网页端产物目录，默认 ../web/dist
 */
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { openDb } from './db.js';
import { isAuthorized } from './auth.js';
import { parseBatchText } from './importer.js';
import {
  createNote,
  createNotes,
  updateNoteContent,
  deleteNote,
  getNote,
  listNotes,
  listCategories,
  listDue,
  countDue,
  reviewNote,
  listDailyLogs,
  createDailyLog,
  deleteDailyLog,
} from './notes.js';
import { pull, push } from './sync.js';
import { aiRoutes } from './ai.js';

const SERVER_DIR = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');

const TOKEN = process.env.LOSNOTE_TOKEN || '';
const PORT = Number(process.env.LOSNOTE_PORT || 8787);
const DB_PATH = process.env.LOSNOTE_DB || join(SERVER_DIR, 'data', 'losnote.db');
// 用 resolve 归一化：否则 LOSNOTE_STATIC 里带 .. 时，下面的 startsWith 校验会把所有请求判成 403
const STATIC_DIR = resolve(process.env.LOSNOTE_STATIC || join(SERVER_DIR, '..', 'web', 'dist'));

const db = openDb(DB_PATH);

/* ---------------- HTTP 小工具 ---------------- */

function send(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'access-control-allow-origin': '*',
    'cache-control': 'no-store',
  });
  res.end(body);
}

function fail(res, status, message) {
  send(res, status, { error: message });
}

function readBody(req, limit = 8 * 1024 * 1024) {
  return new Promise((resolveBody, rejectBody) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        rejectBody(Object.assign(new Error('请求体过大'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolveBody(Buffer.concat(chunks).toString('utf8')));
    req.on('error', rejectBody);
  });
}

async function readJson(req) {
  const raw = await readBody(req);
  if (!raw.trim()) return {};
  try {
    return JSON.parse(raw);
  } catch {
    throw Object.assign(new Error('请求体不是合法 JSON'), { status: 400 });
  }
}

/* ---------------- 路由表 ---------------- */

const routes = [];
const route = (method, pattern, handler) => routes.push({ method, pattern, handler });

route('GET', /^\/api\/health$/, () => ({
  ok: true,
  version: '0.1.0',
  time: Date.now(),
}));

route('GET', /^\/api\/notes$/, ({ url }) =>
  listNotes(db, {
    q: url.searchParams.get('q') || '',
    category: url.searchParams.get('category') || '',
    limit: url.searchParams.get('limit') || 200,
    offset: url.searchParams.get('offset') || 0,
  })
);

route('POST', /^\/api\/notes$/, async ({ req }) => {
  const body = await readJson(req);
  return { note: createNote(db, body) };
});

route('GET', /^\/api\/notes\/([^/]+)$/, ({ params }) => {
  const note = getNote(db, params[0]);
  // 软删的记录对 REST 客户端就是不存在（同步接口仍会下发墓碑，走 /api/sync）
  if (!note || note.deleted) throw Object.assign(new Error('题目不存在'), { status: 404 });
  return { note };
});

route('PATCH', /^\/api\/notes\/([^/]+)$/, async ({ req, params }) => {
  const body = await readJson(req);
  const note = updateNoteContent(db, params[0], body);
  if (!note) throw Object.assign(new Error('题目不存在'), { status: 404 });
  return { note };
});

route('DELETE', /^\/api\/notes\/([^/]+)$/, ({ params }) => {
  const ok = deleteNote(db, params[0]);
  if (!ok) throw Object.assign(new Error('题目不存在'), { status: 404 });
  return { ok: true };
});

route('GET', /^\/api\/categories$/, () => ({ items: listCategories(db) }));

route('GET', /^\/api\/quiz\/due$/, ({ url }) => {
  const items = listDue(db, {
    category: url.searchParams.get('category') || '',
    limit: url.searchParams.get('limit') || 100,
    onlyUnknown: url.searchParams.get('onlyUnknown') === '1',
  });
  return { items, dueTotal: countDue(db) };
});

route('POST', /^\/api\/notes\/([^/]+)\/review$/, async ({ req, params }) => {
  const body = await readJson(req);
  const grade = Number(body.grade);
  if (![0, 1, 2].includes(grade)) {
    throw Object.assign(new Error('grade 只能是 0/1/2'), { status: 400 });
  }
  const note = reviewNote(db, params[0], grade);
  if (!note) throw Object.assign(new Error('题目不存在'), { status: 404 });
  return { note };
});

route('POST', /^\/api\/import\/parse$/, async ({ req }) => {
  const body = await readJson(req);
  const category = String(body.category || '未分类').trim() || '未分类';
  const notes = parseBatchText(String(body.text || ''), category);
  return { count: notes.length, notes };
});

route('POST', /^\/api\/import\/commit$/, async ({ req }) => {
  const body = await readJson(req);
  const list = Array.isArray(body.notes) ? body.notes : [];
  const category = String(body.category || '未分类').trim() || '未分类';
  const created = createNotes(
    db,
    list.map((n) => ({
      name: n.name,
      definition: n.definition,
      category: n.category || category,
    }))
  );
  return { count: created.length, items: created };
});

route('GET', /^\/api\/sync$/, ({ url }) =>
  pull(db, {
    since: url.searchParams.get('since') || 0,
    limit: url.searchParams.get('limit') || 500,
  })
);

route('POST', /^\/api\/sync$/, async ({ req }) => push(db, await readJson(req)));

route('GET', /^\/api\/daily$/, ({ url }) => ({
  items: listDailyLogs(db, { limit: url.searchParams.get('limit') || 100 }),
}));

route('POST', /^\/api\/daily$/, async ({ req }) => {
  const body = await readJson(req);
  return { log: createDailyLog(db, body) };
});

route('DELETE', /^\/api\/daily\/([^/]+)$/, ({ params }) => {
  const ok = deleteDailyLog(db, params[0]);
  if (!ok) throw Object.assign(new Error('记录不存在'), { status: 404 });
  return { ok: true };
});

aiRoutes(route, db, readJson);

/* ---------------- 静态文件（网页端） ---------------- */

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.map': 'application/json',
};

async function serveStatic(req, res, pathname) {
  const rel = normalize(decodeURIComponent(pathname)).replace(/^(\.\.[/\\])+/, '');
  let file = resolve(join(STATIC_DIR, rel));
  if (file !== STATIC_DIR && !file.startsWith(STATIC_DIR + sep)) {
    return fail(res, 403, '禁止访问');
  }
  let info = await stat(file).catch(() => null);
  if (info && info.isDirectory()) {
    file = join(file, 'index.html');
    info = await stat(file).catch(() => null);
  }
  if (!info) {
    // SPA 回退：非静态资源一律交给 index.html
    file = join(STATIC_DIR, 'index.html');
    info = await stat(file).catch(() => null);
    if (!info) {
      return fail(res, 404, '网页端未构建：请先在 web/ 下执行 npm run build');
    }
  }
  const buf = await readFile(file);
  res.writeHead(200, {
    'content-type': MIME[extname(file)] || 'application/octet-stream',
    'content-length': buf.length,
    'cache-control': file.endsWith('index.html') ? 'no-cache' : 'public, max-age=3600',
  });
  res.end(buf);
}

/* ---------------- 主循环 ---------------- */

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const path = url.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET,POST,PATCH,DELETE,OPTIONS',
      'access-control-allow-headers': 'authorization,content-type,x-token',
      'access-control-max-age': '86400',
    });
    return res.end();
  }

  if (!path.startsWith('/api/')) {
    try {
      return await serveStatic(req, res, path);
    } catch (err) {
      return fail(res, 500, err.message);
    }
  }

  // API 一律需要令牌（健康检查除外）
  if (path !== '/api/health' && !isAuthorized(req, url, TOKEN)) {
    return fail(res, 401, '令牌无效或缺失');
  }

  for (const r of routes) {
    if (r.method !== req.method) continue;
    const m = r.pattern.exec(path);
    if (!m) continue;
    try {
      const out = await r.handler({ req, res, url, params: m.slice(1) });
      if (out !== undefined && !res.writableEnded) send(res, 200, out);
      return;
    } catch (err) {
      const status = err.status || 500;
      if (status >= 500) console.error('[error]', req.method, path, err);
      return fail(res, status, err.message || '服务器错误');
    }
  }

  return fail(res, 404, `没有这个接口: ${req.method} ${path}`);
});

if (!TOKEN) {
  console.error('缺少 LOSNOTE_TOKEN 环境变量，拒绝启动。');
  console.error('示例：LOSNOTE_TOKEN=你的令牌 npm start');
  process.exit(1);
}

server.listen(PORT, () => {
  console.log(`LosNote server 已启动: http://127.0.0.1:${PORT}`);
  console.log(`数据库: ${DB_PATH}`);
  console.log(`网页端: ${STATIC_DIR}`);
});

export { server, db };
