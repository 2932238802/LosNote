# 项目状态面板

> 维护者：Doc Agent ｜ 最近更新：2026-11-18

## 当前

| 项 | 值 |
|---|---|
| 本次目标 | B：审计并补齐现有文档；同期受理 `CR-001` |
| 当前 CR | `CR-001` 网页端「开始 / 重新抽题」反复出现同一题（**已确认，选项 A**） |
| 当前门禁 | **T-002 G3 r1 通过（2026-11-18）**：P0/P1/P2 = 0；已提交 `[feat] web端 随机抽取` |
| 进行中任务 | `T-001`（DONE，G3 通过）、`T-002`（DONE，G3 r1 通过）、`T-004`（TODO）；`T-003` / `T-005` 为 `BLOCKED` |
| 阻塞 | `T-003`：Playwright npm 包未安装（浏览器缓存已存在）；`T-005`：公网部署权限/授权未确认 |
| 下一步 | `T-003`（浏览器 E2E）/ `T-004`（性能）→ G4 总审 → `T-005` 发布验收 |
| 线上版本 | 用户观测于 `https://losnote.los-theseus.site/`（2026-11-18 检查：站点与 `/api/health` 均 200，未授权 `/api/quiz/due` 401）；源码版本按 `docs/deploy.md:168` 记录为 `3dfb6e2`，早于当前 HEAD `bfc2876`，是否升级 `[待确认]` |
| 工作区 | 未提交：`server/src/notes.js`（`ORDER BY RANDOM()`）+ `server/test/rest.test.js`（新增 2 个用例）；`cd server && npm test` = `42/42/0`（Doc 2026-11-18 复核实跑） |

## 已完成（本轮）

- 文档差异审计：`docs/reports/doc-agent-2026-11-18-audit.md`
- 新建：`00-overview.md`、`00-requirements-intake.md`、`01-requirements.md`、`02-architecture.md`、`04-tasks.md`、`05-decisions.md`、`STATUS.md`
- 更新：`docs/sync-design.md` 第 6 节「验证状态」（测试数与安卓构建状态）
- 用户授权后修正根目录过时内容：`README.md`（APK 路径、截图目录、JVM 测试数）、`AGENTS.md`（JVM 测试数、工具链说明、`mobile.mjs`）
- G2：`T-001` 开发完成（报告 `reports/dev-2026-11-18-T-001.md`），服务端测试 42/42/0
- G3：`T-001` 评审**通过**（报告 `reports/review-2026-11-18-T-001.md`）；无 P0/P1/P2，4 条 P3 处理如下：
  - 断言 `>= 2` 提升到 `>= 3`：暂不改测试（由 T-003 浏览器侧覆盖），记录为遗留建议
  - `limit` 随机子集语义：已记录 `D-004`
  - `sync-design.md` 测试数：已更新为 42
  - `CHANGELOG.md`：已建立 `docs/CHANGELOG.md`
- G2（T-002）：网页端避重完成（报告 `reports/dev-2026-11-18-T-002.md`）；`cd web && npm test` = 12/12，`npm run build` 成功；浏览器 E2E 留 T-003
- G3（T-002）：**有条件通过**（报告 `reports/review-2026-11-18-T-002.md`）；P0/P1 = 0，P2 = 2（并发 `start()` 乱序覆盖、`error` 共写），P3 = 4；已派 Dev 返工 P2-1/P2-2，未提交
- G2 r1（T-002）：P2-1/P2-2 返工完成（报告 `reports/dev-2026-11-18-T-002-r1.md`）；新增请求守卫；`web` 单测 16/16、构建成功、`server` 42/42
- G3 r1（T-002）：**通过**（报告 `reports/review-2026-11-18-T-002-r1.md`）；P0/P1/P2=0；剩余 P3：评分与重抽可交错、组件接线无自动化、5xx 透传、AC-001-2 语义已记 `D-005`、文档小漂移已修
- 提交：`[feat] web端 随机抽取`（2026-11-18，包含 T-001/T-002 代码、文档与报告；未推送）

## 待建立

- 无。后续 `04-tasks.md` 的任务状态由 Dev 更新；`reports/` 由各角色写入。

## 最近报告

- [doc-agent-2026-11-18-audit.md](reports/doc-agent-2026-11-18-audit.md)
- [dev-2026-11-18-T-001.md](reports/dev-2026-11-18-T-001.md)
- [review-2026-11-18-T-001.md](reports/review-2026-11-18-T-001.md)
- [dev-2026-11-18-T-002.md](reports/dev-2026-11-18-T-002.md)
- [review-2026-11-18-T-002.md](reports/review-2026-11-18-T-002.md)
- [dev-2026-11-18-T-002-r1.md](reports/dev-2026-11-18-T-002-r1.md)
- [review-2026-11-18-T-002-r1.md](reports/review-2026-11-18-T-002-r1.md)
