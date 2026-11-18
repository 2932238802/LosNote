# 项目状态面板

> 维护者：Doc Agent ｜ 最近更新：2026-11-18

## 当前

| 项 | 值 |
|---|---|
| 本次目标 | B：文档审计；`CR-001`（抽题随机性）；新受理 `CR-002`（update.sh）与 `CR-003`（网页 favicon） |
| 当前 CR | `CR-001` 进行中（`T-003`/`T-004`）；`CR-002`/`CR-003` **G1 已确认，G2 进行中** |
| 当前门禁 | `CR-001`：`T-002` G3 r1 通过并已提交 `b1fc00a`；`CR-002`/`CR-003`：`T-006`/`T-007` **G3 通过**，已提交 `[feat] 网页图标与更新脚本` |
| 进行中任务 | `T-006`（DONE，G3 通过）、`T-007`（DONE，G3 通过）；`T-003`（BLOCKED）、`T-004`（TODO）；`T-008`（BLOCKED） |
| 阻塞 | `T-003`：Playwright npm 包未安装；`T-005`/`T-008`：服务器部署/执行授权未确认 |
| 下一步 | `T-008` 服务器实跑 `update.sh` → G4 总审；`CR-001` 的 `T-003`/`T-004` 继续 |
| 线上版本 | 服务器 `/opt/losnote` 曾停在 `bfc2876`；当前 GitHub `main` = `b1fc00a`；服务器是否已拉取并重建 `[待确认]` |
| 工作区 | 已提交 `b1fc00a` 并推送到 `origin/main`；`CR-002`/`CR-003` 新增文档尚未提交；未跟踪 `.claude/` 本地设置 |

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
- 提交：`b1fc00a` `[feat] web端 随机抽取`（2026-11-18）；已推送到 `origin/main`（SSH）
- `CR-002` / `CR-003` G0 已确认：需求卡、`R-002`/`R-003`、`T-006`–`T-008` 已写入；G1 已确认（2026-11-18），G2 进行中
- G2（T-006）：`update.sh` 实现完成（报告 `reports/dev-2026-11-18-T-006.md`）；`bash -n`、`--help`、前置失败/并发/假 docker 逻辑通过；真实 Docker 归 T-008
- G2（T-007）：favicon 完成（报告 `reports/dev-2026-11-18-T-007.md`）；`web` 16/16、构建成功、三资源 200/MIME；真实浏览器视觉归人工/T-003 工具
- G3（T-007）：**通过**（报告 `reports/review-2026-11-18-T-007.md`）；P0/P1/P2=0；P3：16px 金星偏淡、Dev 报告像素示意、金色 token 小差异；`A-005` 已确认
- G3（T-006）：**有条件通过**（报告 `reports/review-2026-11-18-T-006.md`）；P0/P1=0，P2=5，P3=8；已派 Dev 返工 P2-1..P2-4；Doc 已修 deploy.md 与 CHANGELOG/决策/任务状态
- G3 r1（T-006）：**不通过**（报告 `reports/review-2026-11-18-T-006-r1.md`）；P1 锁竞态，已改 flock
- G3 r2（T-006）：**有条件通过**（报告 `reports/review-2026-11-18-T-006-r2.md`）；P2 kill flock 监督进程，已派 r3
- G3 r3（T-006）：**有条件通过**（报告 `reports/review-2026-11-18-T-006-r3.md`）；新 P2 并发双败 TOCTOU；已派 r4
- G3 r4（T-006）：**通过**（报告 `reports/review-2026-11-18-T-006-r4.md`）；P0/P1/P2=0；50/50 并发每轮恰好 1 个 worker；真实 Docker 归 T-008
- 提交：`[feat] 网页图标与更新脚本`（2026-11-18，含 T-006/T-007 代码、文档与报告；未推送）

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
- [dev-2026-11-18-T-006.md](reports/dev-2026-11-18-T-006.md)
- [dev-2026-11-18-T-007.md](reports/dev-2026-11-18-T-007.md)
- [review-2026-11-18-T-007.md](reports/review-2026-11-18-T-007.md)
- [review-2026-11-18-T-006.md](reports/review-2026-11-18-T-006.md)
- [review-2026-11-18-T-006-r1.md](reports/review-2026-11-18-T-006-r1.md)
- [review-2026-11-18-T-006-r2.md](reports/review-2026-11-18-T-006-r2.md)
- [review-2026-11-18-T-006-r3.md](reports/review-2026-11-18-T-006-r3.md)
- [review-2026-11-18-T-006-r4.md](reports/review-2026-11-18-T-006-r4.md)
