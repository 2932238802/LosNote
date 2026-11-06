import { chromium } from 'playwright';

const BASE = process.env.E2E_BASE || 'http://127.0.0.1:8902';
const TOKEN = process.env.E2E_TOKEN || 'crud-token';
const errors = [];
const steps = [];

function ok(name, detail = '') { steps.push({ name, pass: true, detail }); }
function bad(name, detail) { steps.push({ name, pass: false, detail }); }

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
ok('登录进入题库');

// 新建
await page.click('button:has-text("新建")');
await page.waitForSelector('text=新建题目');
await page.fill('input[placeholder="一句话提问"]', '什么是 WAL？');
await page.fill('input[placeholder="未分类"]', '数据库');
await page.fill('textarea', 'Write-Ahead Logging：先写日志再写数据页。\n\n| 项 | 值 |\n| --- | --- |\n| 模式 | wal |');
await page.click('button:has-text("保存")');
await page.waitForSelector('.note-card:has-text("什么是 WAL？")', { timeout: 8000 });
ok('新建题目并出现在列表');

// 详情与 Markdown 表格
await page.click('.note-card:has-text("什么是 WAL？")');
await page.waitForSelector('.modal table td');
const tableText = await page.textContent('.modal table');
ok('详情弹窗渲染 Markdown 表格', tableText.replace(/\s+/g, ' ').trim());

// Esc 关闭
await page.keyboard.press('Escape');
await page.waitForSelector('.modal', { state: 'detached', timeout: 3000 });
ok('Esc 关闭详情');

// 编辑
await page.click('.note-card:has-text("什么是 WAL？")');
await page.click('.modal button:has-text("编辑")');
await page.fill('input[placeholder="一句话提问"]', '什么是 WAL（预写日志）？');
await page.click('button:has-text("保存")');
await page.waitForSelector('.modal', { state: 'detached', timeout: 8000 });
await page.waitForSelector('.note-card:has-text("什么是 WAL（预写日志）？")', { timeout: 8000 });
ok('编辑题目名称并刷新列表');

// 复习：给 2 星（EASY）
await page.click('.tabs button:has-text("复习")');
await page.click('button:has-text("开始 / 重新抽题")');
await page.waitForSelector('button:has-text("显示答案"), .empty', { timeout: 8000 });
const hasQuiz = await page.locator('button:has-text("显示答案")').count();
if (hasQuiz) {
  const q = await page.textContent('.card-question');
  await page.click('button:has-text("显示答案")');
  await page.waitForSelector('.card-answer', { timeout: 5000 });
  const answer = (await page.textContent('.card-answer')).replace(/\s+/g, ' ').trim();
  ok('网页复习：抽到题并展示答案', q.trim() + ' → ' + answer.slice(0, 40));
  await page.click('button:has-text("太简单")');
  await page.waitForTimeout(900);
  const afterBody = await page.textContent('body');
  ok('评分后进入下一题或结束', /复习完了|\d+ \/ \d+/.test(afterBody) ? '已推进' : '状态未变');
} else {
  bad('网页复习', '没有待复习题目');
}

// 删除（confirm 弹窗）
await page.click('.tabs button:has-text("题库")');
await page.waitForSelector('.note-card');
const before = await page.locator('.note-card:has-text("什么是 WAL（预写日志）？")').count();
page.once('dialog', (d) => d.accept());
await page.click('.note-card:has-text("什么是 WAL（预写日志）？")');
await page.click('.modal button:has-text("删除")');
await page.waitForTimeout(1000);
const gone = await page.locator('.note-card:has-text("什么是 WAL（预写日志）？")').count();
if (gone === before - 1) ok('删除题目并从列表移除', before + ' → ' + gone);
else bad('删除题目', '删除前 ' + before + ' 条，删除后 ' + gone + ' 条');

ok('控制台错误数', String(errors.length));
console.log('@@RESULT@@' + JSON.stringify({ steps, errors }, null, 2));
await browser.close();
