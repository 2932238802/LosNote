/**
 * 手机浏览器视口检查（iPhone 12 尺寸 390×844 @3x）：
 * 每个页面不能出现横向滚动、按钮触控高度不能小于 36px、控制台不能报错。
 *
 * 跑法（服务端要先起来，见 web/e2e/README.md）：
 *   PLAYWRIGHT_BROWSERS_PATH=$PWD/.toolchain/pw-browsers \
 *   E2E_BASE=http://127.0.0.1:8902 E2E_TOKEN=crud-token \
 *   node web/e2e/mobile.mjs [截图目录]
 */
import { chromium } from 'playwright';

const BASE = process.env.E2E_BASE || 'http://127.0.0.1:8902';
const TOKEN = process.env.E2E_TOKEN || 'crud-token';
const SHOTS = process.argv[2] || '';

const errors = [];
const report = [];

const browser = await chromium.launch({
  chromiumSandbox: false,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--disable-setuid-sandbox'],
});
const ctx = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
});
const page = await ctx.newPage();
page.setDefaultTimeout(10000);
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));

async function shot(name) {
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true });
}

/** 检查横向溢出：scrollWidth 不能超过视口，并列出越界的元素 */
async function checkOverflow(label) {
  const m = await page.evaluate(() => {
    const wide = [...document.querySelectorAll('body *')]
      .filter((el) => el.getBoundingClientRect().right > window.innerWidth + 1)
      .slice(0, 5)
      .map((el) => el.tagName.toLowerCase() + '.' + String(el.className || '').split(' ')[0]);
    return { scroll: document.documentElement.scrollWidth, win: window.innerWidth, wide };
  });
  report.push({ label, ...m, ok: m.scroll <= m.win + 1 });
}

await page.goto(BASE, { waitUntil: 'networkidle' });
await shot('m1-login');
await page.fill('input[placeholder="访问令牌"]', TOKEN);
await page.click('button:has-text("进入")');
await page.waitForSelector('.note-card', { timeout: 10000 });
await page.waitForTimeout(300);
await checkOverflow('bank');
await shot('m2-bank');

await page.locator('.note-card').first().click();
await page.waitForTimeout(500);
await checkOverflow('detail');
await shot('m3-detail');

// 弹层打开时背景列表不能跟着滚（手机上很容易滚飞）
await page.mouse.wheel(0, 2500);
await page.waitForTimeout(250);
const bgScroll = await page.evaluate(() => window.scrollY);
report.push({ label: 'detail-scroll-lock', scroll: bgScroll, ok: bgScroll === 0 });
await page.keyboard.press('Escape');
await page.waitForTimeout(200);
const restored = await page.evaluate(() => getComputedStyle(document.body).overflow);
report.push({ label: 'body-scroll-restored', overflow: restored, ok: restored !== 'hidden' });

for (const [tab, label, name] of [
  ['复习', 'quiz', 'm4-quiz'],
  ['导入', 'import', 'm5-import'],
  ['设置', 'settings', 'm6-settings'],
]) {
  await page.click(`.tabs button:has-text("${tab}")`);
  await page.waitForTimeout(600);
  await checkOverflow(label);
  await shot(name);
}

// 触控目标：手指点得中的按钮高度
const smallButtons = await page.evaluate(() =>
  [...document.querySelectorAll('button')]
    .map((b) => ({ text: (b.textContent || '').trim().slice(0, 12), h: Math.round(b.getBoundingClientRect().height) }))
    .filter((b) => b.h > 0 && b.h < 36)
);

const failed = report.filter((r) => !r.ok).concat(smallButtons.length ? [{ label: 'small-buttons' }] : []);
console.log(
  '@@RESULT@@' +
    JSON.stringify(
      {
        pass: failed.length === 0 && errors.length === 0,
        report,
        smallButtons,
        errors,
      },
      null,
      2
    )
);
await browser.close();
