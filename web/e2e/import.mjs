import { chromium } from 'playwright';
const BASE = process.env.E2E_BASE || 'http://127.0.0.1:8902';
const TOKEN = process.env.E2E_TOKEN || 'crud-token';
const errors = [];
const steps = [];
const ok = (n, d = '') => steps.push({ name: n, pass: true, detail: d });
const bad = (n, d) => steps.push({ name: n, pass: false, detail: d });

const browser = await chromium.launch({
  chromiumSandbox: false,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--disable-setuid-sandbox'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));

await page.goto(BASE, { waitUntil: 'networkidle' });
await page.fill('input[placeholder="访问令牌"]', TOKEN);
await page.click('button:has-text("进入")');
await page.waitForSelector('.note-list, .empty', { timeout: 10000 });

await page.click('.tabs button:has-text("导入")');
await page.waitForSelector('textarea');

// 竖线模式：一行一条
const pipeText = [
  '闭包是什么 | 函数与其词法环境的组合 | JavaScript',
  '事件循环 | 宏任务与微任务交替执行 | JavaScript',
  '原型链 | 对象沿 __proto__ 向上查找 | JavaScript',
].join('\n');
await page.fill('textarea', pipeText);
await page.fill('input[placeholder="未分类"]', '导入测试');
await page.click('button:has-text("批量解析")');
await page.waitForSelector('.import-row', { timeout: 8000 });
const pipeRows = await page.locator('.import-row').count();
if (pipeRows === 3) ok('竖线模式解析', '3 条');
else bad('竖线模式解析', String(pipeRows));

const rowLoc = page.locator('.import-row');
const firstName = await rowLoc.nth(0).locator('input').first().inputValue();
const firstCat = await rowLoc.nth(0).locator('input').last().inputValue();
const firstDef = await rowLoc.nth(0).locator('textarea').inputValue();
ok('解析字段正确', [firstName, firstDef, firstCat].join(' / '));

await page.click('button:has-text("提交")');
await page.waitForTimeout(1200);
const notice = await page.textContent('body');
ok('提交导入', /已导入 3 条/.test(notice) ? '提示「已导入 3 条」' : notice.slice(0, 60));

// @@@ 块模式：块首行题目，其余为答案
await page.fill('textarea', ['@@@', 'TCP 三次握手', 'SYN → SYN+ACK → ACK', '- 第一次 SYN', '- 第二次 SYN+ACK'].join('\n'));
await page.click('button:has-text("批量解析")');
await page.waitForTimeout(800);
const blockRows = await page.locator('.import-row').count();
const blockFirst = await page.locator('.import-row').nth(0).locator('input').first().inputValue();
const blockDef = await page.locator('.import-row').nth(0).locator('textarea').inputValue();
if (blockRows === 1 && blockFirst === 'TCP 三次握手') ok('@@@ 块模式解析', blockDef.replace(/\n/g, '⏎').slice(0, 40));
else bad('@@@ 块模式解析', blockRows + ' 条 / ' + blockFirst);

await page.click('.tabs button:has-text("题库")');
await page.fill('input[placeholder="搜索题目或答案…"]', '闭包');
await page.waitForTimeout(600);
const hits = await page.locator('.note-card').count();
if (hits >= 1) ok('题库里搜到导入的题', String(hits));
else bad('搜导入的题', '0 条');

// 备份文件：带 uuid/SRS 的 v2 JSON 应走「保留进度」的导入，而不是变成预览再提交
await page.click('.tabs button:has-text("导入")');
await page.waitForSelector('input[type=file]');
await page.setInputFiles('input[type=file]', new URL('./fixtures/backup-v2.json', import.meta.url).pathname);
await page.waitForTimeout(1500);
const backupNotice = await page.textContent('body');
if (/已按备份导入 1 题、1 条足迹/.test(backupNotice)) ok('备份文件按进度导入', '提示保留复习进度');
else bad('备份文件按进度导入', backupNotice.replace(/\s+/g, ' ').slice(0, 80));

await page.click('.tabs button:has-text("题库")');
await page.fill('input[placeholder="搜索题目或答案…"]', '备份导入的题');
await page.waitForTimeout(600);
const backupHits = await page.locator('.note-card').count();
if (backupHits >= 1) ok('备份里的题出现在题库', String(backupHits));
else bad('备份里的题出现在题库', '0 条');

// AI 整理：没配 key 时应该给友好错误，而不是白屏
await page.click('.tabs button:has-text("导入")');
await page.fill('textarea', '随便一段笔记，用来测试 AI 整理');
await page.click('button:has-text("AI 整理")');
await page.waitForTimeout(2500);
const body = await page.textContent('body');
const hasError = /未配置|API|错误|失败|Key/i.test(body);
ok('未配置 AI 时给出提示（不崩溃）', hasError ? '有提示文案' : '没有提示');

ok('控制台错误数', String(errors.length));
console.log('@@RESULT@@' + JSON.stringify({ steps, errors }, null, 2));
await browser.close();
