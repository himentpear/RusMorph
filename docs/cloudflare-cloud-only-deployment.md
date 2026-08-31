# RusMorph 云端语音部署

## 架构

Android 客户端只访问 `https://api.namchieh.org/`。同一个 Cloudflare Worker 提供：

- `POST /api/asr/transcribe`：Workers AI Whisper 俄语识别与单词时间戳。
- `POST /api/pronunciation/analyze`：基于识别匹配、时间戳、语速和停顿的单词级可懂度代理评分。
- `POST /v1/pronunciation-example`：Workers AI 生成俄语例句。
- `GET /review/`：老师人工复核网页，支持按《大学俄语1 对话》课号抽题、浏览器录音、逐词标注、人工评分和机器复评。
- `GET /health`：LLM 和语音绑定状态，不返回密钥。

普通学习音频在一次请求内直接传给 Workers AI，不写入 R2、KV 或本地磁盘。只有老师在复核网页明确同意后，校准录音才写入私有 R2、结构化标注写入 D1；提交者可立即删除自己的样本，R2 生命周期规则也会在 90 天后自动删除录音。Worker 天然按请求运行，无 Windows 后台进程、MFA、FFmpeg 或常驻服务器。

## 评分边界

云端轻量版不是 MFA/GOP 或音素级声学评分。它用 Whisper 的识别结果、segment 置信度和单词时间戳生成“识别可懂度代理评分”，适合直观标色和点击回放；不能用于声称音素、重音或发音器官级诊断。

## 部署

在 `agent-proxy` 目录执行：

```powershell
npm ci
npm test
npm run typecheck
npx wrangler types ./src/worker-configuration.d.ts
npx wrangler d1 migrations apply rusmorph-review-calibration --remote
npx wrangler deploy --dry-run
npx wrangler deploy
```

首次使用先运行 `npx wrangler login`。部署后在 Cloudflare 控制台把 Worker 的 Custom Domain 设为 `api.namchieh.org`，再验证：

```powershell
Invoke-RestMethod https://api.namchieh.org/health
```

确认例句已经正常走 Workers AI 后，可交互式删除遗留 DeepSeek secret：

```powershell
npx wrangler secret delete DEEPSEEK_API_KEY
```

删除 secret 是远端变更，仅在确认不再需要 DeepSeek 回退后执行。

## 成本与休眠

本方案使用普通 Worker + Workers AI，并以 D1/R2 承载小规模人工校准数据；不使用 Cloudflare Containers、KV 或常驻云主机。低日活下可优先利用 Cloudflare 免费额度，实际用量仍应在控制台监控。

Cloudflare Worker 使用 V8 isolate：请求到达时执行，isolate 可以随时被回收，不存在需要维持的应用进程或按空闲时间计费的实例。它不是传统虚拟机意义上的“先开机再启动服务”；空闲即无请求计算，下一次请求由 Cloudflare 自动恢复执行。官方说明 V8 isolate 避免了虚拟机/容器运行时的传统冷启动开销。

截至 2026-08-10，官方免费额度为：

| 项目 | 免费额度 | 本项目影响 |
| --- | ---: | --- |
| Workers 请求 | 100,000 次/天 | 低日活通常远低于额度 |
| Worker CPU | 每次请求 10 ms | 仅做 multipart 校验、文本匹配和轻量评分；AI 等待不占本地模型 CPU |
| Workers AI | 10,000 Neurons/天 | Whisper 和例句 LLM 共用该额度 |
| Whisper large-v3-turbo | 46.63 Neurons/音频分钟 | 若完全不调用 LLM，理论约 214 分钟音频/天 |

例句生成使用的 Llama 也会消耗同一份 10,000 Neurons/天，因此 214 分钟只是“仅 Whisper”的上限，不是承诺容量。免费计划超过每日 AI 额度后请求会失败，需要等待额度重置或升级，不会在未升级付费计划时自动产生超额账单。

生产配置增加两层 Cloudflare 原生限流：语音接口每个来源每分钟 10 次，其他 AI 生成接口每分钟 20 次；限流发生在调用 Workers AI 之前，用于降低额度被异常请求耗尽的风险。Cloudflare 原生限流按数据中心最终一致，不应视为严格的账单上限。

官方依据：

- [Workers 定价](https://developers.cloudflare.com/workers/platform/pricing/)
- [Workers AI 定价](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [Workers 运行与 isolate 生命周期](https://developers.cloudflare.com/workers/reference/how-workers-works/)
- [Workers 原生限流](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)

中国大陆访问标准 Cloudflare 全球网络的时延会受跨境网络影响。当前方案优先满足零常驻成本和低日活；若未来稳定性要求高于成本，应将同一 API 适配器迁移到具备中国大陆节点和备案域名的云厂商，而不是在本机恢复服务。

## 当前生产验证

2026-08-10 已部署 Worker 版本 `9a36da09-467a-4df9-8216-9941072a2d40`：

- Worker 启动时间为 11 ms，生产域名为 `https://api.namchieh.org`。
- 部署输出确认 `AI`、D1、R2、静态资源以及三个原生限流 binding 均已绑定。
- `/review/` 返回 200，并带 CSP 与仅同源麦克风权限策略；错误邀请码返回 401，正确邀请码登录、会话、AI 任务生成、统计与登出闭环均通过。
- D1 migration `0001_review_calibration.sql` 已应用到 APAC 生产库；R2 规则 `review-audio-90-days` 已启用，作用于 `review-audio/` 前缀。
- `/health` 返回 200（0.53 秒）；静音 ASR 返回 200（6.15 秒）；静音发音分析返回 200（1.46 秒）且 `score_available=false`，没有伪造评分。
- AI 例句接口返回 200（2.09 秒），示例为 `Привет, как тебя зовут?`。
- Worker 测试 55/55 通过，Android 单元测试 46/46 通过；Debug 与 Release APK 均成功构建。
- Android Debug/Release 的生成 BuildConfig 只包含 `https://api.namchieh.org/`，设备本地 URL 为空，明文 HTTP 已禁用。

## 回滚

列出版本并回滚：

```powershell
npx wrangler versions list
npx wrangler rollback
```

Android 已固定使用 HTTPS 云端地址。若回滚到不含语音路由的 Worker，语音模块会暂时不可用，但不会重新连接局域网或启动 Windows 后台。
