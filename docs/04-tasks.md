# 任务与追溯矩阵

> 维护者：Doc Agent ｜ 最近更新：2026-11-18 ｜ 状态：`CR-001` 的 `T-001`/`T-002` G3 通过；`CR-002` `T-006` **G3 r4 通过**、`T-008` BLOCKED；`CR-003` `T-007` G3 通过
> 来源：`CR-001`（选项 A）/ `CR-002`（稳健版）/ `CR-003`（新矢量标记 + 仅标签页）
> 任务状态只用：`TODO` / `DOING` / `DONE` / `BLOCKED` / `DROPPED`

## 关键路径

- `CR-001`：`T-001 → T-002 → T-003`；`T-004` 可与 `T-002` 并行；`T-005` 依赖全部完成并由用户发布。
- `CR-002`：`T-006 → T-008`（T-008 需服务器执行环境）。
- `CR-003`：`T-007` 独立，可与其它任务并行。

### T-001 服务端到期题改为随机排序（状态：DONE）

- 来源：`CR-001` / `R-001 v1`
- 关联需求：`AC-001-1`（服务端部分）、`AC-001-5`、`AC-001-7`
- 目标：`GET /api/quiz/due` 每次返回的到期题顺序不再固定，栏目筛选仍生效。
- 涉及文件/模块：`server/src/notes.js`（`listDue`）、`server/test/rest.test.js` 或新增测试文件。
- 前置依赖：无。
- DoR：
  - [x] AC 可测：多次请求结果集合相同、顺序不恒定；栏目筛选仍只返回该栏目。
  - [x] 依赖就绪：Node v24.22.1 可用；`cd server && npm test` 已验证 40 个全过。
  - [x] 关键决策已记录：`CR-001` 选项 A；不加字段、不改响应结构。
  - [x] 影响面已分析：仅 `listDue` 排序；无数据迁移与配置变更。
  - [x] 测试数据/环境可用：可在测试中用临时 SQLite 构造多道到期题。
  - [x] 规模 S。
- 验证方式：
  - `cd server && npm test` 全绿；
  - 新增用例至少断言：同一数据集连续 10 次查询，首题不恒定（允许重复，但不得 10 次全相同）；`category` 过滤后只出现该栏目题。
  - 手工：`GET /api/quiz/due?limit=100` 连续请求 10 次，记录首题，对照 `AC-001-1`。
- 回归范围：`server/test/rest.test.js` 全部用例；`server/test/sync.test.js` 中 `listDue` 到期集合断言；安卓同步不依赖该接口顺序。
- 发布/回滚：随服务端镜像发布；回滚 = 退回上一镜像/commit。无数据库迁移。
- 风险：`ORDER BY RANDOM()` 在大结果集上的性能（由 `T-004` 验证）；工作区已有未提交改动，Dev 需确认是否沿用。
- 完成记录：2026-11-18，沿用工作区 `ORDER BY RANDOM()`（`server/src/notes.js:362-364`），新增 2 个服务端用例；`cd server && npm test` = `tests 42 / pass 42 / fail 0`；报告 `docs/reports/dev-2026-11-18-T-001.md`。
- G3 记录：2026-11-18 Review Agent 判定 **通过**（无 P0/P1/P2，4 条 P3）；报告 `docs/reports/review-2026-11-18-T-001.md`。P3 处理：断言 `>=2` 提升 `>=3` 暂留（T-003 浏览器侧覆盖）；`limit` 随机子集语义见 `D-004`；`sync-design.md` 测试数已更新 42；`CHANGELOG.md` 已建立。
- 规模：S

### T-002 网页端「重新抽题」避免连续重复（状态：DONE）

- 来源：`CR-001` / `R-001 v1`
- 关联需求：`AC-001-2`、`AC-001-3`、`AC-001-4`、`AC-001-6`
- 目标：到期题 > 1 时，连续两次点击「开始 / 重新抽题」首题不同；单题/无题/请求失败时不崩溃。
- 涉及文件/模块：`web/src/views/QuizView.vue`（`start()` 与当前题选择）。
- 前置依赖：`T-001`（服务端随机；前端避重可独立实现，但端到端验收以两者合并为准）。
- DoR：
  - [x] AC 可测：连续两次首题不同；单题允许重复；无题显示空状态。
  - [x] 依赖就绪：`T-001` 完成后合入。
  - [x] 关键决策已记录：`CR-001` 选项 A；不改路由与组件对外接口。
  - [x] 影响面已分析：仅复习页抽题；题库/导入/设置不受影响。
  - [x] 测试数据/环境可用：`cd web && npm install && npm run build`；`web/node_modules` 已由本任务安装。
  - [x] 规模 S。
- 验证方式：
  - `cd web && npm run build` 成功；
  - 手工（或 `T-003` 自动化）：≥5 道到期题，连续点击 20 次，首题至少 3 道不同且无连续两次相同；
  - 单题、无题、断网/401 三种情况各手动跑一次。
- 回归范围：复习页显示答案、三档评分、键盘快捷键、栏目切换；`web/e2e/crud.mjs` 的复习流程。
- 发布/回滚：重新构建 `web/dist` 并随服务端发布；回滚 = 使用上一版 `web/dist`。
- 风险：避重逻辑与栏目切换、空队列的交互；不要破坏“评分后进入下一题”的现有行为。
- 完成记录：2026-11-18，新增 `web/src/quiz-order.js`（本地洗牌 + 避重纯函数）与 `web/src/quiz-error.js`（401/断网文案），接入 `web/src/views/QuizView.vue`（r0 行号 `:120-137`；r1 后 `start()` 位于 `:128-149`）；`cd web && npm test` = `tests 12 / pass 12 / fail 0`（`node --test`，零新增依赖）；`cd web && npm run build` 成功（vite 6.4.5，63 modules）。浏览器 E2E 未跑（Playwright 属 `T-003`）。报告 `docs/reports/dev-2026-11-18-T-002.md`。
- 返工记录（r1，2026-11-18）：G3「有条件通过」的 2 条 P2 已修。P2-1：新增 `web/src/request-guard.js`，`start()` 只应用最后一次请求的响应，加载中禁用栏目下拉；P2-2：拆分 `categoriesError` 与取题 `error`，`loadCategories()` 走 `quizErrorText()`，栏目失败不再误显示「取题失败」。新增 `web/test/request-guard.test.js` 4 例；`cd web && npm test` = `tests 16 / pass 16 / fail 0`；`npm run build` 成功（64 modules）；`cd server && npm test` = 42/42。状态 `DOING → DONE`。报告 `docs/reports/dev-2026-11-18-T-002-r1.md`。
- G3 记录：r0 为「有条件通过」（P2×2）；r1 复审 **通过**（P0/P1/P2=0，仅 P3）。报告 `docs/reports/review-2026-11-18-T-002-r1.md`。
- 规模：S

### T-003 补浏览器端回归用例（状态：BLOCKED）

- 来源：`CR-001` / `R-001 v1`
- 关联需求：`AC-001-1`、`AC-001-2`、`AC-001-3`、`AC-001-4`、`AC-001-5`
- 目标：用真实 Chromium 覆盖「连续点击重新抽题」的随机性与避重，防止回归。
- 涉及文件/模块：`web/e2e/`（新增 `quiz-random.mjs` 或扩展 `crud.mjs`）。
- 前置依赖：`T-002`。
- DoR：
  - [x] AC 可测。
  - [ ] **测试环境未就绪**：`web/node_modules` 已安装；Playwright 浏览器缓存 `~/.cache/ms-playwright/chromium-1254` 已存在，但 `playwright` npm 包未安装。需执行 `web/e2e/README.md` 的准备步骤（`npm i -D playwright`，浏览器可复用缓存）。**缺什么**：`playwright` npm 包。**由谁补**：Dev（获授权安装）或用户/文档侧准备。
  - [x] 关键决策已记录：测试脚本复用现有 `E2E_BASE` / `E2E_TOKEN`。
  - [x] 影响面已分析：只增测试，不改业务代码。
  - [x] 规模 S。
- 验证方式：起临时服务（`LOSNOTE_TOKEN=... LOSNOTE_PORT=8902`）→ 跑新增脚本 → 退出码 0；脚本内断言 `AC-001-1` / `AC-001-2`，并覆盖单题、无题、栏目筛选。
- 回归范围：现有 `crud.mjs` / `import.mjs` / `export.mjs` / `mobile.mjs`。
- 发布/回滚：无（测试代码）。
- 风险：随机断言的稳定性；避免统计型 flaky 断言，优先用“连续两次不同”和“20 次至少 3 种”这类确定性下界。
- 规模：S

### T-004 验证 1000 道到期题下的响应时间（状态：TODO）

- 来源：`CR-001` / `R-001 v1`
- 关联需求：`AC-001-8`
- 目标：确认随机排序不会让到期查询明显变慢。
- 涉及文件/模块：`server/test/`（性能用例或独立基准脚本）；`server/src/notes.js`。
- 前置依赖：`T-001`。
- DoR：
  - [x] AC 可测：同一机器、冷/热缓存各 3 次取中位数 < 100ms。
  - [x] 依赖就绪：Node + `node:sqlite` 可用。
  - [x] 关键决策已记录：若超标，Dev 可提议改用随机抽样（需回写 `05-decisions.md`）。
  - [x] 测试数据/环境可用：临时库插入 1000 条到期题。
  - [x] 规模 S。
- 验证方式：新增基准脚本/测试，输出 3 次耗时与中位数；结果写入 `reports/dev-*.md`。
- 回归范围：无功能变更；不影响现有测试。
- 发布/回滚：无独立发布；若改实现则随 `T-001`。
- 风险：本机性能不代表生产服务器；结论需标注环境。
- 规模：S

### T-005 发布到公网实例并验收（状态：BLOCKED）

- 来源：`CR-001` / `R-001 v1`
- 关联需求：`AC-001-1`、`AC-001-2`（公网环境）
- 目标：把 `T-001` + `T-002` 发布到 `https://losnote.los-theseus.site/`，在真实环境验证。
- 涉及文件/模块：部署包/镜像；`docs/deploy.md`。
- 前置依赖：`T-001`、`T-002`、`T-003`。
- DoR：
  - [ ] **部署权限/凭据未在文档中提供**：线上实例由用户维护（`docs/deploy.md:163-178`），Dev 是否有权发布 `[待确认]`。**缺什么**：发布授权与访问方式。**由谁补**：用户。
  - [x] 验收步骤可复现：浏览器打开公网站点，≥5 道到期题，连续点击 20 次。
  - [x] 规模 S。
- 验证方式：按 `AC-001-1` / `AC-001-2` 在公网环境手工验收，并截图/记录首题序列。
- 回归范围：网页端登录、题库、导入、设置；手机端同步。
- 发布/回滚：按 `docs/deploy.md` 发布新包；回滚 = 切回上一发布目录/镜像，数据卷不受影响。
- 风险：线上数据与测试数据不同；到期题不足 5 道时需先补测试题或改在预发环境验收。
- 规模：S

### T-006 实现根目录 update.sh（状态：DONE）

- 来源：`CR-002` / `R-002 v1`
- 关联需求：`AC-002-1`、`AC-002-2`、`AC-002-3`、`AC-002-6`、`AC-002-7`、`AC-002-8`、`AC-002-9`
- 目标：一条命令完成服务器拉新、备份、重建与验证；失败即停并给出回滚信息。
- 涉及文件/模块：新增根目录 `update.sh`；`README.md`、`docs/deploy.md`、`AGENTS.md` 补充用法。
- 前置依赖：无。
- DoR：
  - [x] AC 可测：浏览器/终端命令可判定；失败路径有明确退出码。
  - [x] 依赖就绪：bash、git、Node 可用；Docker Compose v2 语义已在 `Dockerfile` / `docker-compose.yml` 中确认。
  - [x] 关键决策已记录：`CR-002` 稳健版；参考 Vesta `update.sh` 的 L1–L5 思路。
  - [x] 影响面已分析：仅新增运维脚本与文档；不改应用代码/API/数据。
  - [x] 测试数据/环境可用：`bash -n` 与 `--help` 可在本机验证；**端到端 Docker 行为本机无 Docker，转 `T-008`**。
  - [x] 规模 M。
- 验证方式：
  - `bash -n update.sh` 语法通过；
  - `bash update.sh --help` 退出 0 且列出选项；
  - 静态检查：脚本不含 `LOSNOTE_TOKEN` 值、不出现 `docker volume rm` / `reset --hard`；
  - 失败前置检查可用临时目录/伪 git 仓库模拟（不触碰真实数据）。
- 回归范围：无运行时影响；检查 `README.md` / `docs/deploy.md` / `AGENTS.md` 的现有部署命令仍有效。
- 发布/回滚：新增文件；回滚 = 删除脚本或 revert 该文件。无数据迁移。
- 风险：本机无 Docker，运行时行为只能静态审查；服务器环境差异（端口、Compose 版本、备份路径）。
- G3 记录：2026-11-18 首次评审 **有条件通过**（P0/P1=0，P2=5，P3=8）；已返工 P2-1..P2-4 与低成本 P3，报告 `docs/reports/review-2026-11-18-T-006.md`。
- G3 r1 记录：2026-11-18 复审 **不通过**（P1 锁竞态）；已改 flock，报告 `docs/reports/review-2026-11-18-T-006-r1.md`。
- G3 r2 记录：2026-11-18 复审 **有条件通过**（P2 kill flock 监督进程；已派 r3），报告 `docs/reports/review-2026-11-18-T-006-r2.md`。
- G3 r3 记录：2026-11-18 复审 **有条件通过**（新增 P2 并发双败 TOCTOU）；报告 `docs/reports/review-2026-11-18-T-006-r3.md`。
- G3 r4 记录：2026-11-18 复审 **通过**（P0/P1/P2=0，仅 P3）：50/50 并发每轮恰好 1 个 worker、失败方均有锁冲突提示；只杀 wrapper 后 worker 持 fd 9、第二实例 0 次 compose version；worker 自身 exit 75 不误报。报告 `docs/reports/review-2026-11-18-T-006-r4.md`。真实 Docker 归 `T-008`。
- 规模：M

### T-007 网页端 favicon 与 index.html 声明（状态：DONE）

- 来源：`CR-003` / `R-003 v1`
- 关联需求：`AC-003-1`–`AC-003-7`
- 目标：标签页显示新矢量标记，SVG + ICO/PNG 回退均可访问。
- 涉及文件/模块：新增 `web/public/favicon.svg`、`favicon.ico`、`favicon-32.png`；修改 `web/index.html`。
- 前置依赖：`A-005` 视觉方向确认。
- DoR：
  - [x] AC 可测：构建产物、HTTP 状态、Content-Type、人工 16px 观察。
  - [x] 依赖就绪：`cd web && npm run build` 可用；Vite public 目录规则明确。
  - [x] 关键决策已记录：新矢量标记、仅标签页、品牌色沿用 App。
  - [x] 影响面已分析：仅静态资源与 index.html；不改 API/组件逻辑。
  - [x] 测试数据/环境可用：本机无 ImageMagick/Pillow，需用 Node 生成或复用现有 PNG；`npm run build` 可验证落盘。
  - [x] 规模 S。
- 验证方式：
  - `cd web && npm run build` 成功，`web/dist/favicon.svg`、`favicon.ico`、`favicon-32.png` 存在；
  - 起静态服务或 `vite preview`，`curl -I` 三个资源均为 200 且 Content-Type 正确；
  - 浏览器标签页人工观察 16px/32px；浅色/深色标签页各一次。
- 回归范围：网页构建、Docker 多阶段构建（`COPY web/` 与 `web/dist`）、首页加载。
- 发布/回滚：随 `web/dist` / 镜像发布；回滚 = 还原 `index.html` 与删除新增图标文件。
- 风险：favicon 强缓存导致验收需硬刷新；16px 细节丢失；SVG 在旧浏览器不生效需确认 ICO 回退。
- 完成记录：2026-11-18，新增 `web/public/favicon.svg` / `favicon.ico`（16/32/48）/ `favicon-32.png`，`web/index.html:6-8` 三条 icon link；`cd web && npm test` = 16/16；`npm run build` 成功（64 modules）；真实静态托管三资源 200 + MIME 正确。报告 `docs/reports/dev-2026-11-18-T-007.md`。
- G3 记录：2026-11-18 Review Agent 判定 **通过**（无 P0/P1/P2，3 条 P3：16px 金星偏淡、Dev 报告像素示意不精确、品牌金色 token 小差异）；报告 `docs/reports/review-2026-11-18-T-007.md`。真实浏览器标签页视觉与 Docker 镜像内路径为残留人工验收项。
- 规模：S

### T-008 在服务器实跑 update.sh 验收（状态：BLOCKED）

- 来源：`CR-002` / `R-002 v1`
- 关联需求：`AC-002-4`、`AC-002-5`、`AC-002-6`、`AC-002-9`
- 目标：在真实服务器上验证拉取、备份、重建、容器启动时间、健康检查与前端指纹。
- 涉及文件/模块：服务器 `/opt/losnote`；`update.sh`。
- 前置依赖：`T-006`。
- DoR：
  - [ ] **服务器访问/操作授权未确认**：本机无 Docker，Dev 无法端到端执行；需用户提供服务器执行环境或由用户按脚本运行并回传输出。**缺什么**：服务器执行与回传。**由谁补**：用户。
  - [x] 验收步骤可复现：脚本输出 + `docker compose ps` + `/api/health` + 备份文件列表。
  - [x] 规模 S。
- 验证方式：在服务器执行 `bash update.sh`；记录 HEAD、备份文件、容器 StartedAt、health 输出、首页资源指纹；再执行一次验证幂等。
- 回归范围：网页端登录/题库/复习、安卓同步、数据卷完整性。
- 发布/回滚：脚本内置回滚提示；本任务不额外发布。
- 风险：真实数据与停机窗口；备份失败；服务器无法访问 GitHub。
- 规模：S

## AC ↔ 验证追溯矩阵

| AC | 验证方式 | 对应任务 | 状态 |
|---|---|---|---|
| AC-001-1 | 服务端测试（连续 10 次首题不恒定）+ 浏览器 E2E（20 次至少 3 种） | T-001、T-002、T-003 | 服务端 G3 通过（T-001）；T-002 逻辑层 G3 r1 通过（老服务端固定顺序下 20 次 ≥3 种）；浏览器待 T-003 |
| AC-001-2 | 纯函数单测（50 组种子 × 连续 20 次首题避重）+ 浏览器 E2E + 手工 | T-002、T-003 | T-002 逻辑层 + G3 r1 通过；浏览器待 T-003 |
| AC-001-3 | 纯函数单测（单题允许重复）+ 浏览器 E2E / 手工 | T-002、T-003 | T-002 逻辑层 + G3 r1 通过；浏览器待 T-003 |
| AC-001-4 | 纯函数单测（0 题返回空队列）+ 浏览器 E2E / 手工（空状态） | T-002、T-003 | T-002 逻辑层 + G3 r1 通过；浏览器待 T-003 |
| AC-001-5 | 服务端测试（category 过滤）+ 浏览器 E2E | T-001、T-002、T-003 | 服务端 G3 通过（T-001，category/onlyUnknown/limit）；T-002 未改栏目参数且单测断言队列不丢题；r1 已加请求序号守卫防乱序覆盖；浏览器待 T-003 |
| AC-001-6 | 纯函数单测（401/断网文案）+ 手工 / 浏览器 E2E（恢复后可重试） | T-002、T-003 | T-002 文案映射已单测，r1 已拆分 `categoriesError` 与取题 `error`，G3 r1 通过；浏览器恢复重试待 T-003 |
| AC-001-7 | 现有服务端契约测试 + 新增响应结构断言 | T-001 | G3 通过（T-001） |
| AC-001-8 | 性能基准（1000 道到期题，3 次中位数 < 100ms） | T-004 | 待验证 |
| AC-002-1 | 伪 git 仓库 / 缺 `.env` / 缺 docker 的静态与命令验证 | T-006 | G3 通过 |
| AC-002-2 | 服务器 `git fetch/pull --ff-only` + `HEAD == origin/main` | T-006、T-008 | 本机通过；真实服务器待 T-008 |
| AC-002-3 | 服务器检查备份文件、SQLite 可读、保留策略 | T-006、T-008 | 本机逻辑层通过；真实卷待 T-008 |
| AC-002-4 | `docker compose up -d --build` + `docker inspect StartedAt` | T-008 | 待 T-008 真实 Docker |
| AC-002-5 | `/api/health` 200 + 首页资源指纹 | T-008 | 待 T-008 真实镜像 |
| AC-002-6 | 人为制造失败，检查退出码、日志、回滚提示 | T-006、T-008 | G3 通过；真实日志待 T-008 |
| AC-002-7 | 两个实例并发执行，第二个立即退出 | T-006、T-008 | G3 r4 通过（50/50 恰好一个 worker）；真实服务器待 T-008 |
| AC-002-8 | `--help`、`--no-pull`、token 不落日志 | T-006 | G3 通过 |
| AC-002-9 | 连续执行两次，检查容器/备份/数据 | T-008 | 待 T-008 真实 Docker |
| AC-003-1 | 检查 `web/index.html` 的 link 标签 | T-007 | G3 通过（三条 link） |
| AC-003-2 | `curl -I` 三个图标资源与 Content-Type | T-007 | G3 通过（200 + MIME + sha256） |
| AC-003-3 | `npm run build` 后检查 `web/dist`；Docker 镜像内路径 | T-007 | G3 通过（dist；Docker 镜像内待实跑） |
| AC-003-4 | 16px 人工观察 / 截图 | T-007 | G3 通过（像素解码；真实标签页待人工） |
| AC-003-5 | 浅色/深色标签页人工观察 | T-007 | G3 通过（对比度计算；实机待人工） |
| AC-003-6 | 检查 HTML/SVG 无外部 URL | T-007 | G3 通过（3139B，无外链） |
| AC-003-7 | 禁用 SVG favicon 或旧浏览器回退检查 | T-007 | G3 通过（ICO 16/32/48 结构合法；旧浏览器实机待人工） |
