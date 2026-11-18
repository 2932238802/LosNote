# LosNote 项目总览

> 维护者：Doc Agent ｜ 最近更新：2026-11-18 ｜ 状态：草稿（随审计补齐）
> 证据标注：`[事实]` 代码/配置可直接看到；`[推断]` 基于命名与结构；`[待确认]` 需人回答。

## 1. 一句话定位

给个人使用的间隔重复笔记/题库应用：手机端离线复习，网页端管理与复习，自建 Node 服务做双向同步。`[事实]`（`README.md:3-9`）

## 2. 目标与非目标

**目标**

- 手机端：抽查翻卡、SRS 三档评分、今日足迹、批量/文件/AI 导入、题库 CRUD、JSON 备份。
- 网页端：题库浏览/搜索、Markdown 渲染、在线 CRUD、批量/AI 导入、网页复习评分、备份导入导出。
- 同步：手机 ↔ 服务器 ↔ 网页，字段组 last-write-wins，删除用墓碑传播。

**非目标（明确不做）**

- 不做多账号/多租户：单用户单令牌。`[事实]`（`README.md:139`、`docs/sync-design.md:4`）
- 不做离线同步队列自动重试：失败只提示，下次打开 App 再试。`[事实]`（`README.md:140`）
- 安卓端 `StatsFragment`（学习统计）与 `RoamFragment`（AI 漫游）暂未接入底部导航。`[事实]`（`MainActivity.java:21-24` 只注册四个 Fragment）
- 不做容器编排/K8s 等复杂部署：单容器 + SQLite 文件。

## 3. 运行方式与验证状态

| 目标 | 命令 | 验证状态（2026-11-18） |
|---|---|---|
| 服务端启动 | `cd server && LOSNOTE_TOKEN=<token> npm start`（默认 8787，库 `server/data/losnote.db`） | 未在本次审计启动；命令来自 `README.md:36`、`server/package.json` |
| 服务端测试 | `cd server && npm test` | **已实跑**：`tests 40 / pass 40 / fail 0` |
| 网页开发 | `cd web && npm install && npm run dev`（默认 5174，`/api` 代理到 8787） | 未复跑；见 `README.md:51-57` |
| 网页构建 | `cd web && npm run build` → `web/dist` | 未复跑 |
| 网页 E2E | 见 `web/e2e/README.md`；`crud.mjs` / `import.mjs` / `export.mjs` / `mobile.mjs` | 未复跑（本机未装依赖） |
| 安卓构建 | `./gradlew assembleDebug`（Windows：`gradlew.bat`） | 未复跑；本机无 `.toolchain/`。README 记录曾用 JDK 21 + AGP 8.13 构建通过 |
| 安卓 JVM 测试 | `bash scripts/test-android.sh` | 未复跑；静态统计 `@Test` 25 个 |
| 跨语言同步 E2E | `bash scripts/e2e-sync.sh` | 未复跑 |
| 容器部署 | `docker compose up -d --build` | 未复跑；步骤见 `docs/deploy.md` |

版本基线：`versionName = "1.2"`、`versionCode = 3`、`minSdk 24`、`targetSdk 36`。`[事实]`（`app/build.gradle.kts:15-18`）

## 4. 目录地图

| 路径 | 一句话职责 |
|---|---|
| `app/` | Android 客户端（纯 Java，单模块 `:app`，包名 `com.example.losnote`） |
| `server/` | Node ≥22 后端，零运行时依赖（`node:http` + `node:sqlite`），SQLite 单文件 |
| `web/` | Vue 3 + Vite 网页端，构建产物 `web/dist` 由 server 静态托管 |
| `docs/` | 流程文档与既有设计/部署文档（本目录） |
| `scripts/` | `test-android.sh`、`e2e-sync.sh`、`make-release.sh` |
| `gradle/` | Gradle wrapper 与版本目录 `libs.versions.toml` |
| `Dockerfile` / `docker-compose.yml` | 多阶段构建网页端并打包运行时镜像 |
| `README.md` | 面向使用者的上手与部署摘要（根目录，不在文档根目录内） |
| `AGENTS.md` | 面向开发 Agent 的仓库约束与注意事项（根目录） |

## 5. 当前状态（2026-11-18）

- 分支 `main`，HEAD `bfc2876`（`[feat] ui更新`）。
- 工作区存在未提交改动：`server/src/notes.js` 将到期题排序从 `ORDER BY next_review ASC, created_at DESC` 改为 `ORDER BY RANDOM()`，并新增 `server/test/rest.test.js` 两个用例；`cd server && npm test` = `42/42/0`（2026-11-18 Doc 复核实跑）。未提交、未部署。`[事实]`（`git status`、`git diff`）
- 线上实例记录于 `docs/deploy.md:163-178`：代码目录 `/opt/losnote-3dfb6e2`，早于当前 HEAD；`3dfb6e2..HEAD` 没有 `server/src/notes.js` 提交变更。线上是否已升级/重启 `[待确认]`。
- 已知缺陷：网页端「开始 / 重新抽题」反复出现同一题 → `CR-001` **已确认（选项 A）**，`R-001` / `T-001`–`T-005` 已写入；`T-001`、`T-002`（含 r1）均 G3 通过，浏览器 E2E 与发布待 `T-003` / `T-005`。
- 服务端测试 42 个全过（2026-11-18 Doc 复核实跑）；安卓与网页测试本轮未复跑。

## 6. 文档索引与职责映射

| 标准职责 | 实际文件 | 状态 |
|---|---|---|
| 00 项目总览 | `docs/00-overview.md` | 本文件（新建） |
| 00 需求受理 | `docs/00-requirements-intake.md` | 新建，`CR-001` 待确认 |
| 01 需求与验收 | `docs/01-requirements.md` | 已建立（`R-001`，G1 待确认） |
| 02 架构 | `docs/02-architecture.md` | 新建 |
| 03 设计（接口/数据模型/同步） | `docs/sync-design.md` | 已有，**沿用原名**；本索引标注其职责 |
| 04 任务与追溯矩阵 | `docs/04-tasks.md` | 已建立（`T-001`–`T-005`，G1 待确认） |
| 05 决策 | `docs/05-decisions.md` | 新建 |
| 运维/部署手册 | `docs/deploy.md` | 已有，非标准集文件，保留原名 |
| 状态面板 | `docs/STATUS.md` | 新建 |
| 变更记录 | `docs/CHANGELOG.md` | 新建（未发布区） |
| 角色报告 | `docs/reports/` | 新建 |

根目录 `README.md` 与 `AGENTS.md` 保留：前者面向使用者，后者面向开发 Agent；审计发现的过时项已在用户授权下修正（APK 路径、测试数量、`mobile.mjs`、工具链说明）。

## 7. 关键术语

| 术语 | 定义 |
|---|---|
| 栏目（category） | 题目的分类标签，可多选筛选；空字符串表示未分类 |
| 到期（due） | `next_review <= now` 或 `review_count == 0` 的题 |
| SRS 三档 | `AGAIN=0` / `GOOD=1` / `EASY=2`；SM-2 变体 |
| 字段组 | 内容组（name/definition/category/createdAt/deleted）与 SRS 组（mastery/intervalDays/easeFactor/reviewCount/nextReview/lastReview/stars），各自 LWW |
| 墓碑（tombstone） | 本地删除后写入的记录，同步时转成 `deleted:true`，防止删除被旧数据复活 |
| 游标（last_seq） | 客户端记录的服务器序号，增量拉取 `seq > last_seq` |
| 足迹（daily log） | 今日记录的一条文本及创建时间 |
| 备份 v2 | `{version:2, notes:[...], dailyLogs:[...]}`，含 uuid 与完整 SRS 进度，不含复习历史 |
| CR / R / AC / T | 需求卡 / 需求 / 验收标准 / 任务；ID 一旦分配不复用 |
