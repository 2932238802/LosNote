# LosNote 服务端 + 网页版 + 同步设计

> 目标：手机继续离线用；网页端可导入/编辑/复习；两边经服务器最终一致。
> 决策：单用户令牌鉴权；完整双向同步，字段组分别 LWW。

## 1. 数据模型（服务器 = 真相，手机本地 SQLite 同构）

### notes

| 字段 | 类型 | 说明 |
|---|---|---|
| uuid | TEXT PK | 全局唯一，客户端首次上报即为权威 |
| name | TEXT | 名称 / 题目 |
| definition | TEXT | 定义 / 内容（Markdown） |
| category | TEXT | 栏目 |
| created_at | INTEGER | 创建时间（毫秒） |
| mastery | INTEGER | 0 未掌握 / 1 已掌握 |
| interval_days | INTEGER | SRS 间隔（天），0 = 10 分钟后 |
| ease_factor | INTEGER | 难度系数 ×100，初始 250 |
| review_count | INTEGER | 已复习次数 |
| next_review | INTEGER | 下次应复习时间戳 |
| last_review | INTEGER | 上次复习时间戳 |
| stars | INTEGER | 累计星数 |
| content_ts | INTEGER | **内容组**最后修改时间 |
| srs_ts | INTEGER | **SRS 组**最后修改时间 |
| deleted | INTEGER | 1 = 墓碑（软删） |
| seq | INTEGER | 服务器单调序号，任何写入都会刷新 |

**字段组**是同步冲突的关键：

- 内容组 = `name / definition / category / created_at / deleted` —— 网页和手机都会写
- SRS 组 = `mastery / interval_days / ease_factor / review_count / next_review / last_review / stars` —— 只有"复习"动作会写，网页和手机都写但各写自己的时间点

两组各自按时间戳比较，互不覆盖。这样"网页改了标题"和"手机复习了这条"不会互相打回。

### reviews（只追加）

`uuid` PK、`note_uuid`、`grade`、`time`、`seq`。重复推送按 uuid 幂等忽略。

### daily_logs

`uuid` PK、`content`、`created_at`、`updated_ts`、`deleted`、`seq`。

## 2. 同步协议

客户端保存 `last_seq`（上次拉取到的服务器序号）与本地 `dirty` 标记。

- **拉取**：`GET /api/sync?since=<last_seq>&limit=500`
  返回 `{ seq, hasMore, notes, reviews, daily_logs }`，只含 `seq > since` 的行。
  客户端逐表应用后把 `last_seq` 更新为该次响应的 `seq`；`hasMore=true` 就继续拉直到 false。
- **推送**：`POST /api/sync`，body `{ notes, reviews, dailyLogs }`（只带 dirty 行，camelCase）
  服务器按字段组 LWW 合并，返回 `{ seq, applied, conflicts }`。
  `conflicts` 里是服务器认为更新的行，客户端直接覆盖本地即可收敛。
- 客户端推送成功后清 `dirty` 并把 `last_seq` 更新为返回的 `seq`。

合并规则（服务器）：

```
incoming 行不存在        -> 直接插入
content_ts 更新           -> 覆盖内容组
srs_ts 更新               -> 覆盖 SRS 组
两者都旧                  -> 记入 conflicts，把服务器版本返回
发生任何覆盖              -> 分配新 seq
```

## 3. 手机端迁移（v5 -> v6）

实现见 `app/src/main/java/com/example/losnote/data/DBHelper.java` 的 `migrateV6` / `backfillSyncColumns`：

1. `notes` 加 `uuid / content_ts / srs_ts / dirty / seq`；按本地 id 逐条生成 UUID 回填，
   `content_ts = created_at`，`srs_ts = max(created_at, last_review)`，全部 `dirty = 1`
2. `reviews` 加 `uuid / note_uuid / dirty / seq`，`note_uuid` 由 `LEFT JOIN notes` 回填
3. `daily_logs` 加 `uuid / updated_ts / dirty / seq`，`updated_ts = created_at`
4. 新增 `tombstones(kind, uuid, ts)`（PK `kind+uuid`）与 `sync_state(k, v)`，`last_seq` 存后者
5. 建 6 个索引：三张表的 `uuid` 唯一索引 + `dirty` 索引

**关键设计：本地不加 `deleted` 列。** 删除 = 物理删 + 写墓碑，所以原有 15 个查询方法
不用改一行（都天然看不到已删数据），墓碑本身参与推送，另一台设备拉到 `deleted:true` 后本地再删一次。

> 踩过的坑：`ALTER TABLE ... ADD COLUMN dirty INTEGER DEFAULT 0` 之后，
> `UPDATE ... WHERE dirty IS NULL` 一行都匹配不到（默认值不是 NULL），必须无条件 `SET dirty = 1`，
> 否则老足迹永远不会上传。已修复并用 `node:sqlite` 模拟迁移验证过。

首次同步：`dirty=1` 全量推送，老数据（含 SRS 进度与复习记录）自动搬到服务器。

备份导出升级到 v2（含 uuid 与完整 SRS 进度 + 足迹），导入兼容 v1；复习历史不进备份。

## 4. 网页端功能

题库列表 / 搜索 / 栏目筛选 / Markdown 渲染（表格、删除线、代码高亮）、
新增编辑删除、批量导入（`@@@` 块、`名称 | 定义 | 栏目`、JSON 文件、粘贴）、
AI 整理导入（提示词与去重逻辑与安卓一致）、网页复习（三档评分，同 SM-2 变体）。

## 5. 部署

`docker compose up -d --build`：一个容器跑 Node + SQLite（数据卷 `losnote-data`），前置 Caddy / Nginx 反代并配 HTTPS。
环境变量：`LOSNOTE_TOKEN`（必填，访问令牌）、`LOSNOTE_DB`（默认 `/data/losnote.db`），可选 `LOSNOTE_AI_KEY/BASE/MODEL`。
`Dockerfile` 多阶段：先 `vite build` 出 `web/dist`，再打进最终镜像；服务端零依赖，不需要 `npm install`。

## 6. 验证状态

- `server/` 测试 28 个全过（SRS、批量解析、增量同步、墓碑三例）
- 真实 HTTP 契约测试：字段名与 `SyncClient` 完全一致，墓碑推送后 `deleted=true`、题库列表不再返回
- v5→v6 迁移 SQL 用 `node:sqlite` 模拟跑通（含回填与索引）
- 安卓端本机无 JDK/SDK，未编译 APK；Java 语法用 `java-parser` 全量解析通过（23 个文件 0 失败），布局 XML 全部校验通过
