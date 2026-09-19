# RusMorph（俄语词法助手）

## Production boundaries

- Production Android variants use only `https://api.namchieh.org/`. `localDebug` may use an explicitly configured HTTP development gateway; production variants reject HTTP in both the manifest and client validation.
- A release is never debug-signed. `assembleProductionRelease` requires all four `RUSMORPH_RELEASE_*` signing values and fails clearly when any is absent.
- Dictionary and local learning functions remain available if AI or speech is unavailable. Backup keeps the Room learning database while excluding cache and reserved authentication locations.
- Public speech is Workers AI ASR through the Cloudflare gateway. It is an ASR intelligibility proxy—not MFA/GOP or phoneme-level diagnosis—and response metadata identifies real versus synthetic timing.
- The optional FastAPI precision service is a private gateway backend in production and requires `X-Rusmorph-Internal-Token`; this secret is never shipped in Android.

## 俄语语音输入与朗读纠音

工程现包含独立 FastAPI 服务 [russian_speech_backend](russian_speech_backend/README.md)：

- 搜索页麦克风将 `.m4a` 录音上传到 `/api/asr/transcribe`，高置信度结果进入现有
  `SearchViewModel.setQuery()` 检索链路；中低置信度先显示候选并要求确认。
- 搜索页“进入朗读纠音”可录制已知目标文本，展示整句四项分数、词/音节颜色、音节中文反馈
  和独立分析置信度。
- 后端使用 FFmpeg、faster-whisper、MFA 与 Parselmouth；对齐失败不会生成音节分数。

Android debug 默认连接模拟器宿主 `http://10.0.2.2:8000/`。真机或其他环境可配置：

```powershell
.\gradlew.bat assembleDebug -PSPEECH_BACKEND_BASE_URL=http://192.168.1.10:8000/
.\gradlew.bat assembleRelease -PSPEECH_BACKEND_BASE_URL=https://speech.example.com/
```

release 仅接受 HTTPS。应用只将录音临时写入 cache，上传结束后删除；后端也在请求完成后删除
临时文件。模型安装、CPU/GPU、Docker、API 示例、许可与降级说明见语音后端 README。

## Agent 问答

词条详情页提供“其词源”“其派生”“特殊变格／变位／音变”和“自定义提问”四个入口。入口只准备问题；用户在 Agent 页面明确点击“提问”后，Android 才通过 Cloudflare Worker 发送当前词条上下文。页面支持取消与使用原请求快照重试，不保存长期聊天记录。

调用边界：`WordDetailUiState → KnowledgeRetriever → AgentContextBuilder → AgentRepository → HTTPS Worker → Provider`。本地查词不依赖 Agent；未配置 Worker 或网络失败时 Room 搜索和详情仍正常工作。真实数据没有词条—知识块关联时，`knowledgeContext` 合法地发送空数组并提示资料不足，不通过模糊匹配伪造关联。

公开代理地址通过 Gradle property 或环境变量提供：

```powershell
.\gradlew.bat assembleDebug -PAGENT_PROXY_BASE_URL=http://10.0.2.2:8787/
.\gradlew.bat assembleRelease -PAGENT_PROXY_BASE_URL=https://your-worker.example/
```

HTTP 仅允许 debug 模拟器开发。release 只接受 HTTPS；地址缺失或不合法时 Agent 降级为未配置，本地词典不受影响。API key 不得放进 Gradle property、BuildConfig、资源、Manifest、assets 或源码。Worker 配置详见 `agent-proxy/README.md`。

### 智能命令、词卡与本地库

搜索首页的“智能命令”支持单词查询、课号/词类卡组、例句、辨析、收藏和自定义归档。Router 只生成查询计划，Android 使用 Room 完成真实词库检索后才把当前结果交给 Worker。远端服务不能访问 Room，也不会收到完整词库或完整本地收藏。

本地数据库版本为 v3，使用正式 `MIGRATION_2_3` 新增 `saved_cards`、`saved_decks`、`archives`、归档交叉表及预留复习/作答表。基础词条事实不复制进第二套词库；收藏只保存 `entryId`、用户状态、笔记、生成版本和必要快照。删除归档需要二次确认，同一卡片可以属于多个归档。

RusMorph 是一个面向俄语词汇与词法学习的 Android 应用。应用提供本地查词、形态字段、
来源和本地知识解释。XLSX、CSV 和 DOCX 只在构建期转换；Android 运行时只读取 UTF-8 JSON。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- MVVM、Room、Coroutines/Flow
- Navigation Compose、Retrofit
- Gradle Kotlin DSL 与 Version Catalog
- `applicationId`: `org.namchieh.rusmorph`
- `minSdk`: 26；`compileSdk` / `targetSdk`: 36（本机已安装的最高稳定平台）

## 目录结构

```text
RusMorph/
├── app/                         Android 应用模块
│   └── src/main/java/org/namchieh/rusmorph/
│       ├── data/local/          Room 实体、DAO、数据库
│       ├── data/remote/         Retrofit 接口边界
│       ├── data/repository/     数据仓库
│       └── ui/                  Compose 页面、导航、ViewModel 与主题
├── data-source/                 原始 XLSX/CSV/DOCX，只读输入
├── gradle/libs.versions.toml    统一依赖版本
├── tools/xlsx_builder/          规范化词库构建、审计与验证工具
│   └── config/                  词性别名、知识关联规则与俄语前缀
├── build/reports/data/          XLSX/CSV 审计和纯单词表差异报告
└── build/generated/agent/       后续 Agent/RAG 使用的 JSONL（不打包）
```

## 构建期数据转换与审计

把原始工作簿放在 `data-source/`。工具会扫描全部 `.xlsx`；当前仓库也支持从 UTF-8
`.csv` 主词表构建。解释资料可使用 `.docx`。源文件不会被修改，也不会被打包进 APK。

安装工具依赖：

```shell
python -m pip install -r tools/xlsx_builder/requirements.txt
```

Windows 手动转换：

```bat
py -3 tools/xlsx_builder/build_database_assets.py ^
  --input data-source ^
  --output app/src/main/assets/database ^
  --agent-output build/generated/agent
```

Linux/macOS：

```shell
python3 tools/xlsx_builder/build_database_assets.py \
  --input data-source \
  --output app/src/main/assets/database \
  --agent-output build/generated/agent
```

转换会生成：

- `app/src/main/assets/database/lexicon.json`
- `app/src/main/assets/database/declension_rules.json`
- `app/src/main/assets/database/knowledge_chunks.json`
- `app/src/main/assets/database/data_manifest.json`
- `build/generated/agent/lexicon_agent.jsonl`
- `build/reports/data/xlsx_audit_report.json`
- `build/reports/data/pure_word_validation.json`
- `build/reports/data/knowledge_association_report.json`
- `build/reports/data/manual_review_candidates.json`
- `build/reports/data/real_asset_test_fixtures.json`

`lexicon_agent.jsonl` 每行是一个独立检索对象，供后续 Agent/RAG 使用，不进入 APK。
`xlsx_audit_report.json` 记录表分类、有效行、空白尾行、重复表、未知词性和无法解释的规则。

Gradle 命令：

```powershell
.\gradlew.bat buildLexiconAssets
.\gradlew.bat verifyLexiconAssets
```

```shell
./gradlew buildLexiconAssets
./gradlew verifyLexiconAssets
```

`preBuild` 自动依赖 `buildLexiconAssets`。若发布环境没有原始 XLSX/CSV但已有完整 assets，
构建会保留并验证现有 JSON；若 Python 不在 PATH，可通过 `RUSMORPH_PYTHON` 指定。
`verifyLexiconAssets` 由 Gradle 自身验证 JSON、schema、主外键引用、数量和占位值，不依赖 Python。

数据版本由 schemaVersion、输入文件 SHA-256、生成器版本和规范化配置版本确定。
词性别名、知识关联规则、俄语前缀和 `data-source/knowledge_overrides.json` 的 SHA-256
也参与版本；仅修改配置同样会触发新的运行时数据版本。
`generatedAt` 仅用于审计，不参与 Android 数据版本。Importer 对稳定的运行时 assets 计算
SHA-256，相同版本不会重复导入。

知识关联按人工 override、完整例词、结构化形态字段和受控标签四层评分。低于自动阈值的
派生词或标签候选只进入 `manual_review_candidates.json`，不会写入 Room。Android 本地
`KnowledgeRetriever` 只读取词条已有的交叉引用，最多返回四个知识块，不扫描或发送整个知识库。

## Android 构建

当前公开下载页为 `https://namchieh.org/rusmorph`，APK 直链为
`https://namchieh.org/rusmorph-latest.apk`。版本 `0.2.0` 起，设置页提供
“朗读样本工作台”入口，并通过系统浏览器打开云端录音与人工复核页面。

首次构建需要能够访问 Google Maven 与 Maven Central，并设置 Android SDK：

```shell
./gradlew assembleDebug
```

Windows PowerShell / CMD：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 本地数据层与测试

Room 使用规范化的词条、检索形式、词性、来源、知识块和多对多关联表。应用首次启动时
在后台校验 assets 内容哈希；仅当版本变化时才在单个事务中重新导入。空搜索只读取最近
查看或推荐词条，不扫描返回整个词库。

```powershell
.\gradlew.bat buildLexiconAssets
.\gradlew.bat verifyLexiconAssets
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Windows 下若工程路径含非 ASCII 字符且 JDK fork 测试进程出现 classpath 解码问题，
可从指向同一工程的 ASCII junction 路径运行测试；这不影响 APK 构建或工程内容。

## DeepSeek Agent 链路

AI 调用严格经过 `Android → Cloudflare Worker → DeepSeek`。DeepSeek Secret 不进入 Android、Gradle、assets 或 APK；Android 只保存公开的 Worker 地址。Worker 使用 `deepseek-v4-flash` 进行路由和首次卡片编排，默认关闭思考模式。

本地 Worker 开发、Secret 配置、真实 smoke test、错误排查和密钥轮换说明见 [agent-proxy/README.md](agent-proxy/README.md)。Android debug 默认访问模拟器宿主的 `http://10.0.2.2:8787/`，release 只接受 HTTPS Worker 地址。Worker、网络或模型不可用时，单词与中文释义查询继续使用 Room，并显示不含伪造扩展信息的本地基础卡片。
