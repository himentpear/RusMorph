# WeRus 管理台配置

唯一构建目录 `C:\rusmorph-v006`，地址 `https://werus-admin.namchieh.org`。首个管理员 `himentpear@gmail.com`。所有页面、静态资源和 API 必须先验证 Access JWT，再检查环境白名单与管理 D1 的管理员表。未配置 Access issuer/audience 时整个 Worker 返回 503；没有免登录入口。

## Access 登录

团队域名已指定为 `namchieh-pages.cloudflareaccess.com`，公开 RSA 签名密钥已验证。为 `werus-admin.namchieh.org` 建立 self-hosted 应用，整个域名都受保护，8 小时会话；Allow 策略只包含 `himentpear@gmail.com`，不设置 Bypass 策略。邮箱验证码或已有身份提供商均可。

当前 Wrangler OAuth 可读取应用列表，但读取组织与创建 Access 应用都返回 403。需要限定本账号的 Access Applications/Policies 管理 token。使用指定的团队域名时，可以通过公开签名密钥核验 issuer，无需扩大 token 到组织管理。不要在聊天或仓库中粘贴凭证。脚本 `setup.local.ps1` 使用仓库外当前 Windows 用户 DPAPI 加密凭证；`configure-secrets.ps1` 也支持隐藏输入，并在结束时清理临时变量。Access 应用现已配置，详见部署记录。

两项配置入口（该本机文件不进入 Git）：

```powershell
powershell -NoProfile -File C:\rusmorph-v006\admin-console\scripts\setup.local.ps1
```

手动配置 Access 时：

```powershell
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
node scripts/provision.mjs --access-only
npm run types
npm run deploy
```

脚本将应用 `aud` 与团队 issuer 写入 `wrangler.jsonc`，它们是公开标识，不是密钥。没有这些配置时保持拒绝访问。

## GitHub App

在 GitHub 的个人 Developer settings → GitHub Apps 中创建私有 App，参考 `GITHUB_APP_MANIFEST.json`。只安装到 `himentpear/RusMorph`；权限为 Contents 写、Pull requests 写、Checks 读、Actions 读、Metadata 读。关闭 webhook，后台用 alarm 轮询。不能以管理员 token 绕过仓库保护。PR 检查必须存在成功的 `admin-content-validation`；App 不是保护规则的 bypass actor。

App ID 为 `5261281`，Installation ID 为 `169892441`。本机 PEM 已确认与 App 匹配；安装已通过所需五项最小权限检查，且仅选择 `himentpear/RusMorph`。管理 Worker 的三项 GitHub App secrets 已配置。使用 `scripts/configure-secrets.ps1` 通过 stdin 上传服务端 secrets。脚本在本机将 GitHub PKCS#1 RSA PEM 转为 PKCS#8，无需另存转换后的私钥；禁止把私钥加入 Git。校验还会拒绝多仓库安装或额外权限。

GitHub 的默认分支当前为 `main`，但远端源码与本机 C 基线不同。正式内容发布前必须通过管理源码 PR 纳入 `.github/workflows/admin-content.yml`、编译器、已有内容生成与校验依赖以及经过审核的 canonical 数据。管理台部署不会自动提交或发布本机教材。源码分支前缀 `codex/admin-release-`。App 配置后仍须由管理员在界面确认冻结差异与合并。

## 发布保护与范围

生成的发布 ZIP 在 Worker 中限制解压大小、路径、文件数和 SHA-256。R2 正式路径不能覆盖。构建后逐批上传并读回所有教材与词典引用，完整验证后才原子切换 D1 发布登记。兼容 `current.json` 指针随后同步；同步失败会显示失败阶段，可以幂等重试，D1 登记仍为权威。

结构化编辑保留现有稳定 ID。新增记录、句子拆分数变化、无法唯一映射的词汇链接、不能复现线上词典的 canonical 源，必须走审核过的源码迁移；界面不会让管理员手填新 ID 或覆盖生成字段。目前这种结构新增不能在编辑器中直接提交。

`LIVE_VOICE_ENABLED=false` 保持不变。受管理的 Live 还必须满足后端 `LIVE_VOICE_VERIFIED=true` 与运营开关，不受单一后台开关控制。费率默认空；费用显示“无法估算”，不虚构历史数据。仅管理员可以登记未来生效费率。

## 可复现验证

```powershell
npm ci
npm test
npm run build
npx wrangler deploy --dry-run --outdir .wrangler/smoke
node test/runtime-smoke.mjs
python -m unittest discover -s test -p compiler_test.py
```

首次创建独立管理 Worker，可执行 `scripts/deploy.ps1 -Bootstrap`，暂时省略未部署的 `AGENT_ADMIN` 私有入口。Access 配置未齐时，页面、资源与 API 全部拒绝访问。完成必要后端的可追溯源码、部署与 RPC 验证后，运行不带 Bootstrap 的部署以接回完整服务。Worker 回滚使用 `scripts/rollback-worker.ps1 -VersionId <已验证历史版本>`；教材回滚必须使用管理台内的完整发布任务。

本机视觉检查可运行 `node test/preview.mjs`，只监听 127.0.0.1:8790，使用测试签名和隔离测试库。它不被打包，不能访问生产数据，也不是生产登录入口。

用户与评审库迁移分别位于 `domain-migrations/users`、`domain-migrations/reviews`；管理库迁移位于 `migrations`。`provision.mjs` 检查实际列存在后增量添加，保持原用户和样本，不运行教材导入。初次登记只读取、校验当前线上资源。

## 管理员验收

- Access：未登录页面、JS、API 均不可获取；错误签名、issuer、aud、过期 JWT 与非管理员均被拒绝。退出后重新请求私有资源不能利用浏览器缓存。
- 用户：搜索、概况；开通、续期、撤销与过去到期时间拒绝；两窗口同时修改只有一个成功，操作与审计同库原子记录。
- 内容：稳定 ID 只读，导入只成草稿，校验、差异、冻结与修订冲突；未知关系不能静默重绑；检查线上版本不会因部署变化。
- 发布：缺配置、CI 失败、源码变化、上传失败、资源缺失都不激活；重复提交只执行一次；确认合并受仓库保护；完整历史回滚；兼容指针失败可重试。
- 资源：路径、扩展名、签名、大小、哈希和不可覆盖；正式版本引用受保护，归档不删除物理不可变资源。
- 评审：私有回放、过期录音反馈、保留原始标注、修订冲突、无效样本排除、审计。
- 运营：开关不影响独立发音测评与词典播放；Live 不能绕过部署和真机前提；缺用量或费率不能显示零费用。
- 隐私：统计无对话文本、音频、凭证；重复事件不累计，费率按事件日期，详情 30 天、汇总 365 天。
- 响应式：桌面左导航、手机折叠菜单、横向表格与详情抽屉，加载／空态／重试／保存反馈。

真机语音验收由用户完成。本次不上传小程序、不发布 Android APK、不自动发布本地内容、不自动开启 Live。

管理发布的 canonical 词典路径为 data-source/admin-dictionary/online-reviewed.json；该源已精确重建当前线上 100 词版本。人工复核／答案构建依赖位于 data-source/admin-validation/textbook，编译器先核对它们与未修改线上 datasetHash 一致，再应用编辑。正文、词条和关联事实只来自 SHA-256 校验的线上组件。data_manifest 元数据重新生成只发生于隔离构建目录，差异必须查看 CI 摘要／metadata-diff.json。无草稿的工作流仅检查工具接入，不生成正式发布任务。

sync_pending 表示 D1 登记已切换而 R2 兼容指针尚未同步；任务保持发布锁并自动重试，禁止取消或新发布，避免重复激活。原完整历史版本仍可在同步完成后选择回滚。
