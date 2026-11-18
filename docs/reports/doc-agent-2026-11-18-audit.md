# Doc Agent 文档差异审计报告

> 维护者：Doc Agent ｜ 日期：2026-11-18 ｜ 范围：全仓库文档 vs 当前代码
> 目标类型：B（审计并补齐现有文档）｜ 文档根目录：`/home/losangelous/LosAngelous/LosNote/docs/`

## 0. 结论摘要

- 未发现**完全重复**的文档文件；`README.md` 与 `AGENTS.md` 内容有重叠但读者不同（用户上手 / 开发 Agent 约束），建议保留两份。
- 发现 **1 处文档之间的数字冲突**（服务端测试数 28 vs 40）、**3 处过时陈述**、**若干缺失的标准文档**。
- 本次需求（网页端「开始/重新抽题」不随机）已定位到可复核证据，另立 `CR-001` 待 G0 确认；本报告不替用户做产品决定。
- 用户注释：全仓库扫描 `LosAngelous:` / `LOS:`，**0 处**。

## 1. 审计方法与证据

| 动作 | 命令/位置 | 结果 |
|---|---|---|
| 文档清单 | `find . -name "*.md"`（排除 `node_modules/`、`build/`、`web/dist/`） | 仅 `README.md`、`AGENTS.md`、`docs/deploy.md`、`docs/sync-design.md`、`web/e2e/README.md` |
| 服务端测试 | `cd server && npm test` | `tests 40 / pass 40 / fail 0`（2026-11-18 实跑） |
| 安卓测试静态统计 | `grep -rn "@Test" app/src/test/java` | 25 个（`DBHelperSyncTest` 10、`SyncClientTest` 5、`BackupJsonTest` 4、`UiSmokeTest` 3、`UiShotTest` 1、`SyncServerE2ETest` 1、`ExampleUnitTest` 1） |
| 安卓测试实跑 | `scripts/test-android.sh` | **未执行**：本机无 `.toolchain/`（脚本依赖预置 JDK 21 + Android SDK） |
| 触发排序对比 | `git show HEAD:server/src/notes.js`、`git show 3dfb6e2:server/src/notes.js` | 两个版本均为 `ORDER BY next_review ASC, created_at DESC` |
| 排序行为复现 | `node:sqlite` 内存库，6 道到期题，连续取首题 5 次 | 确定性排序：`n6 n6 n6 n6 n6`；`ORDER BY RANDOM()`：`n4 n4 n6 n1 n5`（仍可能连续重复） |

## 2. 缺失文档（标准文档集）

| 标准文件 | 现状 | 说明 | 处理 |
|---|---|---|---|
| `00-overview.md` | 缺 | 无项目目标/运行方式/目录地图/术语 | 本次新建 |
| `00-requirements-intake.md` | 缺 | 无需求卡、假设池 | 本次新建（含 `CR-001`） |
| `01-requirements.md` | 缺 | 无正式 R/AC | 待 `CR-001` 过 G0 后建立 |
| `02-architecture.md` | 缺 | 架构信息散落在 README/AGENTS/sync-design | 本次新建 |
| `03-design.md` | 部分有 | `docs/sync-design.md` 已覆盖数据模型与同步协议 | **不重命名**，在 `00-overview.md` 索引中标注其对应职责 |
| `04-tasks.md` | 缺 | 无任务 ID 与追溯矩阵 | 待 G1 后建立 |
| `05-decisions.md` | 缺 | 决策散落 | 本次新建，收录可复核的既有决策 |
| `STATUS.md` | 缺 | 无门禁/状态面板 | 本次新建 |
| `reports/` | 缺 | 无角色报告目录 | 本次新建并写入本报告 |

## 3. 过时与冲突清单

| 编号 | 文档位置 | 文档声称 | 代码/可复核事实 | 判定 | 处理建议 |
|---|---|---|---|---|---|
| C-01 | `README.md:14` | 安装 `dist/losnote-v1.1-debug.apk` | `app/build.gradle.kts:17-18`：`versionCode = 3`、`versionName = "1.2"`；仓库内无 `dist/` 目录 | 过时 | 属根目录文档，**本次未改**；建议改为“最新构建产物见 `app/build/outputs/apk/debug/app-debug.apk`（当前 1.2）”，或补 1.2 APK 产物 |
| C-02 | `README.md:124`、`AGENTS.md`「构建」 | JVM 单元测试 21 个 | 静态 `@Test` 共 25 个；其中 `SyncServerE2ETest` 默认跳过、`ExampleUnitTest` 为样板 | 过时 | 属根目录文档，**本次未改**；建议改为“25 个 `@Test`（默认跳过 1 个跨语言 E2E）”，并注明实跑环境 |
| C-03 | `docs/sync-design.md:102` | 服务端测试 28 个全过 | 2026-11-18 实跑 40 个全过 | **文档间冲突** | 已在本次审计中更新为 40（见第 6 节） |
| C-04 | `docs/sync-design.md:105` | 安卓端本机无 JDK/SDK，未编译 APK | `README.md:68`、`README.md:135` 记录已用 JDK 21 + AGP 8.13 构建通过；`AGENTS.md` 给出构建命令 | 过时 | 已更新为“历史陈述，最新状态见 `00-overview.md`”并注明本次未复跑 |
| C-05 | `AGENTS.md`「测试」 | 网页 E2E 只列 `crud.mjs` / `import.mjs` / `export.mjs` | `web/e2e/mobile.mjs` 存在，且 `web/e2e/README.md:41` 有覆盖说明 | 过时（遗漏） | 属根目录文档，本次未改；建议补 `mobile.mjs` |
| C-06 | `docs/deploy.md:163-178` | 已部署实例代码目录 `/opt/losnote-3dfb6e2` | 当前 `HEAD = bfc2876`，且 `3dfb6e2..HEAD` 无 `server/src/notes.js` 变更；线上实例早于当前代码 | 环境状态（非文档错误） | 保留历史记录；另在 `STATUS.md` 标注“线上版本待升级/待确认” |
| C-07 | `docs/sync-design.md:77` | 原有 15 个查询方法 | 本次未逐一核对 | `[待确认]` | 留给 Dev/Review 验证，暂不改写 |

## 4. 重复文件检查

| 候选 | 是否重复 | 结论 |
|---|---|---|
| `README.md` vs `AGENTS.md` | 否（同有构建/测试/架构信息） | 读者不同：README 面向使用者，AGENTS 面向开发 Agent。建议保留，并在 `00-overview.md` 标明分工 |
| `README.md`「同步机制 / 部署 / 备份」 vs `docs/sync-design.md`、`docs/deploy.md` | 否（README 是摘要 + 链接） | 保留：摘要不复制实现细节 |
| `web/e2e/README.md` | 否（局部目录说明） | 保留 |
| `docs/archive/261114` | 空目录，无文件 | 无内容可删；如确认为废弃目录可手动移除（git 不跟踪空目录） |

**结论：本轮没有可安全删除的重复文档文件。**

## 5. 与本次需求相关的证据（供 CR-001）

- 前端：`web/src/views/QuizView.vue:111-118` 的 `start()` 每次重新请求到期题并 `index = 0`；`web/src/views/QuizView.vue:96` 当前题固定取 `queue[0]`。
- 服务端（提交版）：`server/src/notes.js:363`（HEAD 内容）按 `ORDER BY next_review ASC, created_at DESC` 排序 —— 未评分时首题恒定。
- 线上实例：`docs/deploy.md:168` 记录代码目录为 `3dfb6e2`；该提交同样使用上述确定性排序，且之后 `server/src/notes.js` 无提交改动。
- 工作区：存在**未提交**改动，把排序改为 `ORDER BY RANDOM()`（`git diff -- server/src/notes.js`）；未提交也未被任何测试覆盖。
- 复现：6 道到期题、连续 5 次取首题，提交版输出 `n6 n6 n6 n6 n6`；`RANDOM()` 版输出 `n4 n4 n6 n1 n5` —— 说明仅改服务端仍可能出现连续重复。
- 测试缺口：`web/e2e/crud.mjs:57` 只点击一次“开始 / 重新抽题”，未断言多次点击后的差异。

## 6. 本次写入记录

| 文件 | 变更 |
|---|---|
| `docs/reports/doc-agent-2026-11-18-audit.md` | 新建：本报告 |
| `docs/00-overview.md` | 新建：项目目标、运行方式、目录地图、状态、术语、文档索引 |
| `docs/00-requirements-intake.md` | 新建：`CR-001` 需求卡 + 假设 `A-001`–`A-003` |
| `docs/02-architecture.md` | 新建：系统上下文、模块边界、路由、数据流、外部依赖 |
| `docs/05-decisions.md` | 新建：收录可复核的既有决策 `D-001`–`D-003` |
| `docs/STATUS.md` | 新建：门禁与状态面板 |
| `docs/sync-design.md` | 定点更新第 6 节「验证状态」（C-03 / C-04） |

**未修改**：`README.md`、`AGENTS.md`、`docs/deploy.md`（前两者在已确认文档根目录之外；deploy.md 的历史部署记录没有事实错误，状态差异记入 `STATUS.md`）。

## 7. 未验证项

- 安卓 JVM 测试：本机无 `.toolchain/`，**未复跑**；25 只是静态 `@Test` 计数，不等于通过数。
- 网页构建与 Playwright E2E：本机未安装依赖，**未复跑**。
- `docs/sync-design.md:77` 的“15 个查询方法”：未逐一核对。
- 线上实例当前是否已重启/升级到含 `RANDOM()` 的版本：`[待确认]`（`A-001`）。

## 8. 后续变更（G0 确认后，2026-11-18）

用户确认 `CR-001` 选项 A，并授权修正过时内容。在此基础上追加：

- 新建 `docs/01-requirements.md`（`R-001` + `AC-001-1`–`AC-001-8`）与 `docs/04-tasks.md`（`T-001`–`T-005` + 追溯矩阵）。
- 修正根目录 `README.md`：APK 安装路径（C-01）、截图目录（原 `dist/`）、JVM 测试数 21→25（C-02）。
- 修正根目录 `AGENTS.md`：JVM 测试数 21→25（C-02）、`scripts/test-android.sh` 工具链说明、补 `mobile.mjs`（C-05）。
- `docs/sync-design.md` 第 6 节已按 C-03/C-04 更新。
- 审计报告其余未决项：`docs/deploy.md` 线上版本记录（C-06，环境状态，保留）；`docs/sync-design.md:77` 的“15 个查询方法”（C-07，`[待确认]`）。

## 下一步交接

- 交给：**用户**
- 依据：G0 待确认（`CR-001` 选项未选）；审计结果需你裁决根目录文档是否修改
- 携带材料：本报告、`docs/00-requirements-intake.md`（CR-001）、`docs/STATUS.md`
- 给用户的确认事项：
  1. `CR-001` 选择 A/B/C/D/E 哪一项；
  2. 观测环境与到期题数量（`A-001`）；
  3. “足够随机”的判定阈值（`A-002`）；
  4. 是否授权修改根目录 `README.md` / `AGENTS.md` 的过时陈述（C-01/C-02/C-05）。
