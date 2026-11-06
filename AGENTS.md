# LosNote

Java Android 间隔重复笔记/题库应用，支持 Markdown 渲染和 AI 辅助内容管理。

## 构建

```bash
gradlew.bat assembleDebug            # 构建 debug APK
gradlew.bat test                      # JVM 单元测试 21 个（迁移 / LWW / 同步客户端 / 备份互通 / 跨语言 E2E）
scripts/e2e-sync.sh                   # 起真 Node 服务，跑 SyncClient ↔ 服务器 的跨语言 E2E
scripts/test-android.sh               # WSL/Linux：自动配工具链并跑同一批测试
gradlew.bat connectedAndroidTest      # 插桩测试（需设备/模拟器）
```

- AGP 8.13.0, Gradle 8.13, compileSdk/targetSdk 36, minSdk 24
- JDK 17–21（Java 25 与 Gradle 8.13 Kotlin DSL 不兼容）
- `sourceCompatibility = targetCompatibility = JavaVersion.VERSION_11`（`app/build.gradle.kts:30`）
- 阿里云 Maven 镜像优先（`settings.gradle.kts`）；`gradle.properties` 显式清空 `systemProp.*proxy*` 以覆盖全局代理设置
- `local.properties` SDK 路径：`D:\sdk\AndroidPlatform`（WSL 下用 `.toolchain/android-sdk`，该文件不入库）
- 版本目录：`gradle/libs.versions.toml`

## 架构

单模块 `:app`，包名 `com.example.losnote`，纯 Java。

底部导航 4 个 Tab（`MainActivity` 用 `hide/show` 切换 Fragment，非 `replace`）：

| Tab | Fragment | 功能 |
|---|---|---|
| 抽查 | `QuizFragment` | 翻卡 + 左右滑动 + SRS 三档评分 |
| 今日 | `DailyFragment` | 今日足迹记录/删除、连续记录天数、历史足迹 |
| 录入 | `AddFragment` | 单条录入、批量导入（`@@@` 块 / `|` 行）、文件导入、AI 整理导入 |
| 题库 | `BankFragment` | 搜索、CRUD、栏目管理、JSON 备份、AI 去重 |

`StatsFragment`（学习统计）和 `RoamFragment`（AI 漫游）已存在但**未接入**底部导航。

### 关键类

| 文件 | 作用 |
|---|---|
| `data/DBHelper.java` | SQLite 单例，`notes` + `reviews` + `daily_logs` + `tombstones` + `sync_state`，**v6** 含云同步字段 |
| `data/Note.java` | POJO，`easeFactor` 存 int × 100 |
| `data/DailyLog.java` | 今日足迹 POJO（内容 + 创建时间） |
| `data/Srs.java` | SM-2 变体，三档评分：`AGAIN=0` / `GOOD=1` / `EASY=2` |
| `ai/AiConfig.java` | 配置存于 SharedPreferences `"ai_settings"`，支持 `webSearch` 标记 |
| `ai/AiClient.java` | OpenAI 兼容 Chat Completions |
| `sync/SyncConfig.java` | 服务器地址 + 令牌，存 SharedPreferences `"sync_settings"` |
| `sync/SyncClient.java` | 双向增量同步（push/pull `/api/sync`，墓碑表示删除） |
| `sync/SyncResult.java` | 同步结果计数 + 中文文案 |
| `ui/MarkdownRenderer.java` | Markwon + Prism4j + 表格 + 删除线 |
| `ui/PrismGrammar.java` | `@PrismBundle` → 编译生成 `GrammarLocatorImpl` |

## 仓库结构（三端）

| 目录 | 说明 |
|---|---|
| `app/` | Android 客户端（Java，本目录构建） |
| `server/` | Node ≥22 后端，零依赖（`node:http` + `node:sqlite`），SQLite 单文件 |
| `web/` | Vue 3 + Vite 网页端，构建产物 `web/dist` 由 server 静态托管 |
| `docs/sync-design.md` | 同步协议、字段组、v5→v6 迁移说明 |
| `Dockerfile` / `docker-compose.yml` | 一键部署（多阶段构建 web，再打运行时镜像） |

## 手机端 UI 约定（v1.1 起）

- **不要改 view id**：四个 Fragment 和两个 Adapter 都直接 `findViewById`，改 id 会静默返回 null。
- 颜色只用 `values/colors.xml` 里的语义色（`theme_primary` / `text_secondary` / `tag_bg` / `card_bg` …），深浅色各一份在 `values-night/`；**不写死 #RRGGBB**。
- 组件样式集中在 `values/styles.xml`：`Widget.LosNote.Card`（18dp 圆角、白底、1dp 阴影）、`Widget.LosNote.SearchField`（药丸搜索框）、`TextAppearance.LosNote.Display`（28sp 页面标题）。
- 主题里 **必须保留 `elevationOverlayEnabled=false`**：否则 Material 3 会给卡片叠约 7% 主色，白卡变 #EFF1F4 灰。
- `app:itemActiveIndicatorColor` 是 BottomNavigationView 的**子视图属性**，只能写进 style，写进布局会编译报错。
- 想看界面长什么样又没手机：`bash scripts/test-android.sh --tests "com.example.losnote.ui.UiShotTest"`，会用 Robolectric 原生图形把四页渲染成 `app/build/ui-shots/*.png`。

## 注意点

- **纯 Java**，新代码用 Java 写；注释均为中文。
- Prism4j 注解处理器 `prism4j-bundler` 编译期生成 `GrammarLocatorImpl`。
- `configurations.all { exclude(...) }` 全局排除 `org.jetbrains:annotations-java5`（与 androidx 冲突）。
- `knowledge/` 目录存在但为空，按需使用。
- 抽查页（`QuizFragment`）和题库页（`BankFragment`）均支持**多栏目多选**（`SetMultiChoiceItems`），传入 `Collection<String>` 到 DB 层产生 `IN (...)` 查询。
- 批量导入：`@@@` 单独一行分隔块，块首行=名称，其余=定义；否则按行解析 `名称 | 定义 | 栏目`。
- SRS：`nextReview=0` = 未复习/立即可复习；`intervalDays=0` = 10 分钟后复习。到期查询条件：`nextReview <= now OR reviewCount == 0`。
- AI 功能需先在设置中配好 API Key / Base URL / Model。支持 DeepSeek、Qwen、Custom。`AiConfig` 含 `webSearch` 开关。
- AI 去重：先做本地标题归一化 + 编辑距离（相似度 ≥ 0.92）预筛选，再发候选组给 LLM。
- 整库导出两端同源：网页端「设置 → 导出 JSON」走 `GET /api/export`（`notes.js: exportBackup()`），与安卓 `BankFragment.buildBackupJson()` 字段完全一致，可互相导入；导入/导出逻辑抽成了 `BankFragment.importBackupJson()` / `buildBackupJson()` 便于单测。
- JSON 备份格式：v2 `{"version":2,"notes":[...],"dailyLogs":[...]}`，含 uuid 与完整 SRS 进度（`mastery/intervalDays/easeFactor/reviewCount/nextReview/lastReview/stars`）；导入时仍兼容 v1（只有 name/definition/category/mastery）。复习历史 `reviews` 不入备份（`reviewHistoryIncluded: false`）。
- 云同步（v6）：本地删除 = 物理删 + `tombstones` 墓碑，推送时墓碑转成 `{uuid, deleted:true}`；查询层因此**不需要**加 `deleted=0` 过滤。游标存 `sync_state.last_seq`。字段级 last-write-wins：内容组（name/definition/category/createdAt/删除）看 `content_ts`，SRS 组（mastery/intervalDays/easeFactor/reviewCount/nextReview/lastReview/stars）看 `srs_ts`。
- `AndroidManifest.xml` 设了 `android:usesCleartextTraffic="true"`，局域网 `http://IP:8787` 才能连；上公网请用 HTTPS 反代。
- 同步入口：题库页右上「设置」→「云同步」，填地址与令牌。`MainActivity.onStart()` 会自动同步一次。
- `AndroidManifest.xml` 已声明 `INTERNET` 权限。

## 测试

- `app/src/test/java/com/example/losnote/data/DBHelperSyncTest.java`：Robolectric 跑真实 SQLite，覆盖 **v5→v6 迁移**（uuid/时间戳/dirty 回填、幂等重开、墓碑表与游标表）和**字段组 LWW**（内容组/SRS 组各自后写胜出、本地删除墓碑防复活、`markAllSynced` 不误清推送期间的新改动、`deleteAll` 留墓碑）。共 10 个用例。
- Robolectric 需要 `android-all-instrumented` jar：`scripts/test-android.sh` 会自动下到 `.toolchain/robolectric-deps/`（已 gitignore）；`app/build.gradle.kts` 在该目录存在时把 `robolectric.dependency.dir` 指过去，不存在时走默认 `~/.m2`。
- `ExampleUnitTest` / `ExampleInstrumentedTest` 是生成器自带的样板，保留未删。
- 服务端：`cd server && npm test`（40 个用例）。`server/test/rest.test.js` 会真起一个服务进程（临时端口 + 临时库），覆盖令牌鉴权、CRUD、复习、导入解析/提交、AI 设置脱敏、同步增量与墓碑，以及**静态目录带 `..` 也要能托管**（曾因未归一化导致全站 403）。
- 同步客户端：`app/src/test/java/com/example/losnote/sync/SyncClientTest.java` 用裸 Socket 起本地桩服务（android.jar 没有 `com.sun.net.httpserver`），
  走真 `HttpURLConnection` 验证请求体字段/令牌头、墓碑转 deleted、拉取落库并推进游标、远端墓碑删本地题、401 映射成可读错误，共 5 个用例。
- 跨语言 E2E：`app/src/test/java/com/example/losnote/sync/SyncServerE2ETest.java`（默认 skip，靠 `-Plosnote.e2e.server/-Plosnote.e2e.token` 打开）。`scripts/e2e-sync.sh` 会起临时 Node 服务，跑「手机 A 建题+复习 EASY → 同步 → 清库换设备 → 拉回 stars=3/reviewCount=1/intervalDays=4/easeFactor=265 → 第三轮已是最新」。这条链路验证的是 Java 客户端与 Node 服务器之间真正的字段契约。
- 备份互通：`app/src/test/java/com/example/losnote/ui/BackupJsonTest.java` 用 Robolectric 验证「网页导出的 v2 JSON → 手机导入」SRS 不丢、uuid 保留、重复导入幂等、v1 老备份仍兼容。样例 `app/src/test/resources/server-backup-v2.json` 由服务端 `exportBackup()` 真跑生成，不是手写。
- 备份导入（网页端）：`POST /api/import/backup` → `notes.js: importBackup()`，文件为准不做 LWW，保留 uuid 与 SRS，重复 uuid 覆盖、足迹按 uuid 去重；`ImportView` 选到备份文件（有 `version` / `dailyLogs` / 题里有 uuid 或 SRS 字段）时直接走它，普通 `{notes}` 仍进预览。
- 网页端：`web/e2e/crud.mjs`、`web/e2e/import.mjs`、`web/e2e/export.mjs` 是 Playwright 端到端脚本（真实 Chromium），覆盖登录、新建/编辑/删除、Markdown 表格渲染、Esc 关弹层、复习评分、竖线/`@@@` 两种导入格式、未配 AI key 的提示，以及导出备份的文件名与 v2 字段契约、备份文件按进度导入（`web/e2e/fixtures/backup-v2.json`）。跑法见 `web/e2e/README.md`。
