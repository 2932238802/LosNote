# 系统架构

> 维护者：Doc Agent ｜ 最近更新：2026-11-18 ｜ 状态：草稿
> 标注：`[事实]` 代码/配置可见；`[推断]` 基于结构与命名；`[待确认]` 需人回答。

## 1. 系统上下文

```
┌──────────────┐        HTTPS / HTTP + Bearer 令牌        ┌──────────────────┐
│ Android App  │ ───────────────────────────────────────▶ │  Node 服务端      │
│ (Java, SQLite)│ ◀─────────────────────────────────────── │  node:http       │
└──────────────┘         /api/sync 双向增量                 │  node:sqlite     │
                                                           │  + 静态托管 web  │
┌──────────────┐        HTTPS / HTTP + Bearer 令牌        │                  │
│ 浏览器 (Vue) │ ───────────────────────────────────────▶ │                  │
└──────────────┘         REST + 静态资源                    └──────────────────┘
                                                                    │
                                                         可选：OpenAI 兼容 AI 服务
```

- 服务端是同步真相源；手机本地 SQLite 与服务端表结构同构。`[事实]`（`docs/sync-design.md:6`）
- 鉴权：单用户令牌，请求头 `Authorization: Bearer <LOSNOTE_TOKEN>`。`[事实]`（`README.md:37`、`server/src/index.js` 鉴权中间件）

## 2. 模块与边界

| 模块 | 职责 | 不负责 | 依赖 |
|---|---|---|---|
| `app/` Android | 离线题库、复习、足迹、导入、备份、同步客户端 | 不直接读服务端数据库；不提供账号体系 | 本地 SQLite、HTTP、Markdown（Markwon + Prism4j）、可选 AI |
| `server/` Node | REST API、SRS 计算、导入解析、备份、同步合并、静态托管 | 不做 UI 渲染；不跑定时任务；不依赖外部数据库 | `node:http`、`node:sqlite`、文件系统 |
| `web/` Vue | 题库浏览/编辑、复习、导入、设置、主题 | 不持久化业务数据；令牌存浏览器本地 | 服务端 REST、`marked` + DOMPurify + highlight.js |
| `docs/` | 流程、架构、设计与部署文档 | 不参与运行时 | — |

## 3. 服务端路由（事实清单）

来源：`server/src/index.js`

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/health` | 健康检查（无需业务数据） |
| GET | `/api/notes` | 题库列表/搜索/栏目筛选/分页 |
| POST | `/api/notes` | 新建题目 |
| GET | `/api/notes/:uuid` | 单题详情 |
| PATCH | `/api/notes/:uuid` | 修改题目 |
| DELETE | `/api/notes/:uuid` | 删除（墓碑） |
| GET | `/api/categories` | 栏目统计 |
| GET | `/api/export` | 整库备份 v2 |
| GET | `/api/quiz/due` | 到期题列表（支持 `category` / `limit` / `onlyUnknown`） |
| POST | `/api/notes/:uuid/review` | 三档评分并推进 SRS |
| POST | `/api/import/parse` | 批量导入预览解析 |
| POST | `/api/import/commit` | 批量导入提交 |
| POST | `/api/import/backup` | 备份文件导入（保留 uuid/SRS） |
| GET | `/api/sync` | 增量拉取（`since` 游标） |
| POST | `/api/sync` | 增量推送并做字段组 LWW |
| GET | `/api/daily` | 足迹列表 |
| POST | `/api/daily` | 新增足迹 |
| DELETE | `/api/daily/:uuid` | 删除足迹 |

## 4. 关键数据流

### 4.1 手机同步（`SyncClient` ↔ `/api/sync`）

1. `MainActivity.onStart()` 触发一次同步。`[事实]`（`AGENTS.md`）
2. 推送本地 `dirty=1` 的 notes / reviews / dailyLogs；服务端按字段组 LWW 合并，返回 `conflicts`。`[事实]`（`docs/sync-design.md:44-64`）
3. 客户端拉取 `seq > last_seq` 的增量，逐表应用并推进游标；`hasMore=true` 继续拉。`[事实]`（`docs/sync-design.md:48-54`）
4. 删除 = 本地物理删 + `tombstones` 墓碑；推送时转成 `{uuid, deleted:true}`。`[事实]`（`AGENTS.md`「云同步」）

### 4.2 网页复习

1. `QuizView.start()` 调 `GET /api/quiz/due?category=&limit=100`，把返回列表作为本轮队列。`[事实]`（`web/src/views/QuizView.vue:111-118`）
2. 当前题取 `queue[index]`，初始 `index=0`。`[事实]`（`web/src/views/QuizView.vue:96`）
3. 评分调 `POST /api/notes/:uuid/review`，成功后 `index += 1`，本轮不重复追问。`[事实]`（`web/src/views/QuizView.vue:127-142`）
4. 服务端到期查询当前提交版按 `next_review ASC, created_at DESC` 排序；工作区未提交版本改为 `ORDER BY RANDOM()`。`[事实]`（`server/src/notes.js:349-366`）

### 4.3 导入与备份

- `@@@` 分块 / `名称 | 定义 | 栏目` 行格式由服务端 `parseImport` 解析，网页预览后提交。`[事实]`（`AGENTS.md`、`server/src/index.js:163-184`）
- 备份 v2 由 `GET /api/export` 生成，与安卓 `BankFragment.buildBackupJson()` 字段一致；网页导入走 `POST /api/import/backup`，保留 uuid 与 SRS。`[事实]`（`README.md:114-118`、`AGENTS.md`）

## 5. 外部依赖与失败模式

| 依赖 | 用途 | 失败模式 | 当前处理 |
|---|---|---|---|
| SQLite（`node:sqlite`） | 服务端唯一持久化 | 文件损坏/磁盘满 | 无自动恢复；靠 `VACUUM INTO` 快照备份（`docs/deploy.md:77-100`） |
| 浏览器 localStorage | 网页令牌 | 清除后需重新登录 | 无服务端会话 |
| OpenAI 兼容 API（可选） | AI 整理/去重 | 未配 key、超时、限流 | 前端提示未配置；无自动重试 `[推断]` |
| 网络 | 同步 / 网页访问 | 超时、401、明文被拦 | 同步失败只提示，下次 App 启动再试（`README.md:140`）；Android 已开 `usesCleartextTraffic` |

## 6. 技术选型（可观察理由）

- **Node 零依赖**：`server/package.json` 无 `dependencies`，使用 `node:http` + `node:sqlite`，减少部署面。`[事实]`
- **SQLite 单文件**：数据量个人级，备份/迁移简单；WAL 模式，备份必须 `VACUUM INTO`。`[事实]`（`docs/deploy.md:77-81`）
- **纯 Java Android**：项目约束，避免 Kotlin 混编。`[事实]`（`AGENTS.md`）
- **字段组 LWW + 墓碑 + seq 游标**：避免时钟依赖与删除复活；详见 `docs/sync-design.md`。`[事实]`
- **Vue 3 + Vite**：网页端轻量 SPA，构建产物由服务端静态托管。`[事实]`（`web/package.json`、`Dockerfile`）
