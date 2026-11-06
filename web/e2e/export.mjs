import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';

/**
 * 验证网页端「导出 JSON」：下载到的文件必须是安卓能导入的 v2 备份。
 * 前置：server 用 E2E_BASE 起好，且库里至少有 1 条题、1 条足迹。
 */
const BASE = process.env.E2E_BASE || 'http://127.0.0.1:8902';
const TOKEN = process.env.E2E_TOKEN || 'crud-token';

const steps = [];
const errors = [];
const ok = (n, d = '') => steps.push({ name: n, pass: true, detail: d });
const bad = (n, d) => steps.push({ name: n, pass: false, detail: d });

const browser = await chromium.launch({
  chromiumSandbox: false,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--disable-setuid-sandbox'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));

await page.goto(BASE, { waitUntil: 'networkidle' });
await page.fill('input[placeholder="访问令牌"]', TOKEN);
await page.click('button:has-text("进入")');
await page.waitForSelector('.note-list, .empty', { timeout: 10000 });

await page.click('.tabs button:has-text("设置")');
await page.waitForSelector('button:has-text("导出 JSON")');

const [download] = await Promise.all([
  page.waitForEvent('download', { timeout: 15000 }),
  page.click('button:has-text("导出 JSON")'),
]);
const name = download.suggestedFilename();
const file = await download.path();
if (!/^losnote-backup-\d{4}-\d{2}-\d{2}\.json$/.test(name)) bad('下载文件名', name);
else ok('下载文件名', name);

let backup = null;
try {
  backup = JSON.parse(readFileSync(file, 'utf8'));
  ok('备份是合法 JSON', `${JSON.stringify(backup).length} 字节`);
} catch (e) {
  bad('备份 JSON 解析', e.message);
}

if (backup) {
  if (backup.version === 2) ok('version = 2', String(backup.version));
  else bad('version', String(backup.version));
  if (backup.reviewHistoryIncluded === false) ok('标注不含复习明细');
  else bad('reviewHistoryIncluded', String(backup.reviewHistoryIncluded));

  const need = ['uuid', 'name', 'definition', 'category', 'createdAt', 'mastery',
    'intervalDays', 'easeFactor', 'reviewCount', 'nextReview', 'lastReview', 'stars'];
  const first = backup.notes?.[0] || {};
  const missing = need.filter((k) => !(k in first));
  if (backup.notes?.length && missing.length === 0) ok('题字段齐全（安卓 v2 契约）', `${backup.notes.length} 条`);
  else bad('题字段', missing.length ? '缺 ' + missing.join(',') : '没有题');

  if (Array.isArray(backup.dailyLogs) && backup.dailyLogs.length > 0) ok('足迹进备份', `${backup.dailyLogs.length} 条`);
  else bad('足迹', '没有 dailyLogs');

  if (!backup.notes.some((n) => n.deleted === true)) ok('不含墓碑');
  else bad('墓碑', '备份里出现了 deleted 记录');
}

ok('控制台错误数', String(errors.length));
console.log('@@RESULT@@' + JSON.stringify({ steps, errors }, null, 2));
await browser.close();
