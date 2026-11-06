# 网页端端到端测试

用真实 Chromium 跑一遍主流程，验证「浏览器里点得通」，比只看构建产物可靠。

## 准备

```bash
# 1. 装 playwright（在 web/ 下，浏览器下载走 PLAYWRIGHT_BROWSERS_PATH 指定的目录）
cd web
npm i -D playwright --cache ../.npm-cache --logs-dir ../.npm-logs
PLAYWRIGHT_BROWSERS_PATH=$PWD/../.toolchain/pw-browsers npx playwright install chromium

# 2. 起一个测试服务（另开终端）
cd server
LOSNOTE_TOKEN=crud-token LOSNOTE_PORT=8902 \
LOSNOTE_DB=$PWD/data/crud.db LOSNOTE_STATIC=$PWD/../web/dist \
node src/index.js
```

## 跑

```bash
cd web
PLAYWRIGHT_BROWSERS_PATH=$PWD/../.toolchain/pw-browsers \
E2E_BASE=http://127.0.0.1:8902 E2E_TOKEN=crud-token \
node e2e/crud.mjs
node e2e/import.mjs
node e2e/export.mjs
node e2e/mobile.mjs            # 手机视口 390×844：横向溢出 / 触控目标 / 控制台报错
```

WSL 下 Chromium 必须带 `--no-sandbox`（脚本里已经写好），否则启动会挂住。

## 覆盖范围

| 脚本 | 覆盖 |
|---|---|
| `crud.mjs` | 登录、新建、列表、详情 Markdown 表格、Esc 关弹层、编辑、复习抽题+显示答案+评分、删除、控制台 0 报错 |
| `import.mjs` | 竖线格式解析、`@@@` 块格式解析、提交后题库可搜到、未配 AI key 时的提示、备份文件按进度导入（`fixtures/backup-v2.json`） |
| `export.mjs` | 导出 JSON 的文件名、v2 字段契约、含足迹、不含墓碑 |
| `mobile.mjs` | 手机浏览器视口下五个页面都不横向溢出、按钮触控高度 ≥ 36px、控制台 0 报错 |

脚本用 `E2E_BASE` / `E2E_TOKEN` 覆盖默认值；失败时退出码非 0，可直接接 CI。
