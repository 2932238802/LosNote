# LosNote

间隔重复笔记 / 题库应用：安卓端 + 网页端 + 自建同步服务。

- **安卓端**（`app/`，Java）：抽查翻卡、SRS 三档评分、今日足迹、批量导入、AI 整理、题库管理
- **网页端**（`web/`，Vue 3）：题库浏览与搜索、Markdown 渲染、在线增删改、批量/AI 导入、网页复习评分
- **服务端**（`server/`，Node ≥22，零依赖）：REST API + SQLite 单文件 + 静态托管网页端

三端共用一套字段和 SRS 规则，手机在没带在身上时也可以用电脑往题库里存东西，手机联网后自动拉下来。

## 快速开始

### 1. 起服务端

```bash
cd server
LOSNOTE_TOKEN=换个自己的令牌 npm start      # 默认 8787 端口，db 在 server/data/losnote.db
curl -H "Authorization: Bearer 换个自己的令牌" http://127.0.0.1:8787/api/health
```

环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `LOSNOTE_PORT` | `8787` | 监听端口 |
| `LOSNOTE_TOKEN` | 无（**必填**） | 访问令牌，安卓端和网页端都用它 |
| `LOSNOTE_DB` | `server/data/losnote.db` | SQLite 文件路径 |
| `LOSNOTE_AI_KEY` / `LOSNOTE_AI_BASE` / `LOSNOTE_AI_MODEL` | 空 | AI 整理导入的默认密钥，也可在网页「设置」里填 |

### 2. 起网页端（开发模式）

```bash
cd web
npm install
npm run dev        # http://127.0.0.1:5174，/api 自动代理到 127.0.0.1:8787（可用 LOSNOTE_API 覆盖）
```

生产构建 `npm run build`，产物 `web/dist` 由服务端直接托管（`/` 就是网页端）。

> npm 若报 `EROFS` 缓存只读：加 `--cache ../.npm-cache --logs-dir ../.npm-logs`。

### 3. 安卓端

```bash
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
```

需要在 `local.properties` 里指到本机 Android SDK（该文件不入库）。
仓库已用 JDK 21 + AGP 8.13 实测构建通过，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 6.9 MB，minSdk 24）。

装好后：**题库 → 右上「设置」→「云同步」**，填服务器地址（如 `http://192.168.1.10:8787`）和令牌，点「保存并同步」。之后每次打开 App 会静默同步一次。

## 部署到云服务器

```bash
cp .env.example .env        # 填 LOSNOTE_TOKEN
docker compose up -d --build
```

`docker-compose.yml` 默认只监听 `127.0.0.1:8787`，请用 Caddy/Nginx 反代并配 HTTPS（手机端尤其需要，明文 HTTP 只在局域网方便调试）。数据在命名卷 `losnote-data` 里。

完整的部署、HTTPS、备份恢复、排错步骤见 [docs/deploy.md](docs/deploy.md)。

## 同步机制

- 每条记录有 `uuid` + 两个时间戳：`content_ts`（题目内容组）、`srs_ts`（复习进度组），按字段组做 last-write-wins
- 本地删除走「物理删 + `tombstones` 墓碑」，墓碑本身也参与同步，所以另一台设备能收到删除
- 增量靠 `seq` 游标（`sync_state.last_seq`），拉取 `since=游标`，推送成功后按时间戳条件标记 `dirty=0`
- 冲突时服务端把胜出版本回灌到 `conflicts`，客户端直接覆盖本地并清 dirty
- 详细协议与 v5→v6 迁移见 [docs/sync-design.md](docs/sync-design.md)

## 备份格式

安卓端「设置 → 导入/导出」导出 `losnote_backup.json`：

```json
{
  "version": 2,
  "exportedAt": 1730000000000,
  "reviewHistoryIncluded": false,
  "notes": [
    {
      "uuid": "…", "name": "闭包", "definition": "函数 + 环境", "category": "JS",
      "createdAt": 0, "mastery": 1, "intervalDays": 3, "easeFactor": 265,
      "reviewCount": 2, "nextReview": 0, "lastReview": 0, "stars": 5
    }
  ],
  "dailyLogs": [{ "uuid": "…", "content": "读了 20 页", "createdAt": 0 }]
}
```

- 兼容导入旧版 v1（只有 `name/definition/category/mastery`）
- `uuid` 相同视为同一条，重复导入不会产生副本
- 导入后标记为待推送，联网即上传云端
- 复习历史（`reviews` 表）不进备份，只影响「今日已复习」统计

## 测试

```bash
cd server && npm test       # node:test，SRS / 导入解析 / 同步（含墓碑），28 个用例
```

安卓端 `./gradlew assembleDebug` 与 `./gradlew test` 均已实测通过；Docker 镜像也已实际起容器验证（`/api/health`、网页端 200、无令牌 401）。

## 已知限制

- 单用户单令牌，没有多账号
- 无离线队列重试：同步失败只提示，下次打开 App 再试
- `StatsFragment` / `RoamFragment` 仍未接入底部导航
