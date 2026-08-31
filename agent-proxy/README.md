# RusMorph Agent Proxy

Android 只调用部署在 `https://api.namchieh.org/` 的 Cloudflare Worker，不依赖 Windows、本地端口或局域网服务。俄语 ASR、单词时间戳、轻量纠音代理评分和例句生成均通过 Workers AI binding 完成；Android 不会上传完整 Room 词库。

## 生产架构

- 语音模型：`@cf/openai/whisper-large-v3-turbo`
- 例句模型：`@cf/meta/llama-3.1-8b-instruct-fp8-fast`
- 运行方式：Cloudflare V8 isolate 按请求执行，无常驻进程
- 普通学习音频：不写入 R2/KV/磁盘，请求结束即不再由本项目保存
- 人工复核：`/review/` 同域网页；经老师明确同意的校准录音写入私有 R2，标注写入 D1
- 复核题库：以《大学俄语1 对话》中的教材对话为主，支持按第 1–18 课筛选并在任务、标注和历史记录中保存课号；第 8 课原文仅有复习标题，无对话
- 复核录音留存：R2 生命周期规则 `review-audio-90-days` 在 90 天后自动删除；提交者也可在网页立即删除自己的录音与标注
- 额度保护：语音每来源 10 次/分钟，其他 AI 接口每来源 20 次/分钟

本地 `russian_speech_backend/` 和 `scripts/run_agent_proxy.ps1` 仅作为历史开发/离线实验材料，不是 Android 或生产流量的运行依赖。

## 可选 DeepSeek 开发回退

生产配置的 `AGENT_PROVIDER` 为 `cloudflare`，即使账户中保留旧的 DeepSeek Secret 也不会调用它。只有本地显式把 provider 改为 `deepseek` 时才使用以下配置。

本地开发时复制 `.dev.vars.example` 为未提交的 `.dev.vars`：

```text
DEEPSEEK_API_KEY=replace_with_your_key
```

生产或预览环境使用交互式 Secret：

```powershell
npx wrangler secret put DEEPSEEK_API_KEY
```

不要把密钥写入 `wrangler.toml`、README、Android、Gradle、APK 或日志。轮换时在 DeepSeek 平台撤销旧密钥，再重新执行 `wrangler secret put`。

## 命令

```powershell
npm install
npm run typecheck
npm test
npm run dev
npm run deploy
```

首次部署复核通道还需应用 D1 migration；生产资源已存在时不要重复创建：

```powershell
npx wrangler d1 migrations apply rusmorph-review-calibration --local
npx wrangler d1 migrations apply rusmorph-review-calibration --remote
npx wrangler r2 bucket lifecycle list rusmorph-review-audio
```

复核邀请码只以 SHA-256 摘要保存在 Worker 配置中。当前四位 PIN 仅适合小范围受邀校准，不应当作长期高安全认证；扩大教师范围时应迁移到邮箱 OTP 或 Cloudflare Access。

无 `DEEPSEEK_API_KEY` 时，真实 smoke test 会明确输出 `SKIPPED`。DeepSeek 的费用、余额与速率限制需要在平台侧管理。

Android debug 与 release 都默认连接 `https://api.namchieh.org/`，并禁用明文 HTTP。Worker 或模型不可用时，Android 的本地词典搜索仍可使用，但语音和生成式功能会明确报告云端不可用。

常见状态：401 表示 Worker/Provider 认证失败；402 表示额度不足；429 表示限流；500 表示上游内部错误；503 表示服务繁忙。Worker 只向 Android 返回规范化错误，不透传上游正文、Headers 或堆栈。
