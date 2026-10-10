# 管理台部署记录

2026-10-10，管理域名：<https://werus-admin.namchieh.org>。

已部署独立 `werus-admin` Worker，来源提交 `40d5d2c0fa0ebd45b13f7adc085f0f4cfb3a4bc4`，Worker 版本 ID `9d393ec9-dfa4-4b3b-8c1c-605e434cdf5d`。此次使用 `scripts/deploy.ps1 -Bootstrap -SkipTests`，未绑定尚未部署的私有 `AGENT_ADMIN` RPC；现有生产对话后端未随此次部署更新。

线上检查时间 `2026-10-10T09:44:34.245Z`：页面 `/`、`/api/admin/v1/me` 和当前 JS/CSS 均返回 `503 ACCESS_NOT_CONFIGURED`；自定义域名归属正确，`workers.dev` 与预览入口均关闭。该状态表示默认拒绝访问，尚未完成管理员登录启用。

管理 D1：`werus-admin` / `d7b730db-c48f-4293-bcc2-5fbc0c301c02`。当前登记的教材 `2026.09.29.9b73d11c751f`、词典 `2026.10.04.cba48c9d3668` 与原线上版本一致，登记修订号仍为 0。Live 运营开关仍为关闭。未上传本地内容、未激活新版本、未上传小程序或发布 APK。

## 安全配置入口

在本机 PowerShell 执行：

```powershell
powershell -NoProfile -File C:\rusmorph-v006\admin-console\scripts\setup.local.ps1
```

该本机入口文件被 Git 忽略，只含公开标识与仓库外 PEM 的路径。Token 在隐藏提示中输入；不要将 Token 或私钥粘贴到聊天、源码或命令参数。远端 `werus-ai` Worker 中的 Secret 不能读回提供给本地配置程序。

脚本先用原 Wrangler 凭证确认 Worker，再验证 GitHub 安装权限；Access-only Token 只用于 Access 配置，随后恢复原 Wrangler 凭证。GitHub 权限尚未批准时，Access 配置继续，GitHub 密钥不上传；权限批准后可重跑。Access 配置成功只修改本地发行方与受众配置，仍需重新部署并验证真实登录。

GitHub 最新检查仍缺少 Contents / Pull requests 读写与 Checks / Actions / Metadata 只读权限。安装范围必须仅为 `himentpear/RusMorph`；安装 ID `169892441`。首个管理员为 `himentpear@gmail.com`。

## 可复现线上检查

```powershell
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
node scripts/check-online.mjs --bootstrap
```

配置 Access 并重新部署后，去掉 `--bootstrap` 检查未登录请求是否进入正确的 Access 登录页。结果写入忽略的 `.wrangler/online-check.json`，只含检查元数据；其中 `localSourceCommit` 是运行检查时的本地提交，不代表远端部署版本。

待完成的部署与功能范围仍见 `TEST_RECORD.md`：真实管理员登录、GitHub 安装与 CI 全链路、完整后端部署，以及结构新增编辑流程尚未交付通过。

## Access 登录部署（2026-10-10）

Access 应用与精确邮箱白名单已配置，发行方为 `https://namchieh-pages.cloudflareaccess.com`。当前独立管理 Worker 来源提交 `246a5aa6ed540dd70b2dd9ad9398f8c4163d13c0`，版本 ID `ea6365db-3d28-46dd-9419-1236bbbc01de`，启动耗时 18 ms；仍使用 bootstrap 配置，未部署现有业务后端。

北京时间 2026-10-10 18:11 完成线上检查：页面、API 和真实 JS/CSS 均返回 302 并跳转到上述 Access 团队；登录页返回 200，提供邮箱验证码表单。`workers.dev` 与预览入口仍关闭；发布登记修订仍为 0，Live 仍关闭。实际管理员验证码登录与登录后页面验收仍由管理员完成，不能将未登录重定向检查称为真实登录通过。

用户此前添加到本机入口脚本的 Token 已移入仓库外当前用户 Windows DPAPI 加密文件；脚本不再包含明文 Token。本机入口已以 UTF-8 BOM 保存，Windows PowerShell 的中文 PEM 路径已验证可读取。脚本支持 `-AccessTokenFile` 安全复用；对应加密文件仅可由当前 Windows 用户解密，不应拷入源码或上传到 Worker。

GitHub App `templatewerus` 及安装 `169892441` 的权限仍为空。管理员权限更新入口：<https://github.com/settings/apps/templatewerus/permissions>；安装批准入口：<https://github.com/settings/installations/169892441>。在通过最小权限检查前不会向管理 Worker 上传 GitHub 私钥，源码发布链路仍未启用。

最新运行版本：`c381dba5-af92-44d6-8bc7-1ae3b5a9bf20`，来源提交 `7b398ebc57c04b4a738edfcc482d91106db177a9`，包括回滚持久意图与 D1 故障恢复修复，启动耗时 9 ms。北京时间 2026-10-10 18:18 再次验证线上 Access 重定向、关闭的公开入口、未改变的登记版本和关闭的 Live；workerd 页面、资源、API JWT 及同源回归通过。当前管理台测试共 60 项通过。

## GitHub App 配置检查（2026-10-10）

App `5261281` / 安装 `169892441` 已通过最小权限和唯一仓库检查：Contents、Pull requests 写；Checks、Actions、Metadata 读；范围仅 `himentpear/RusMorph`。管理 Worker 已配置 `GITHUB_APP_PRIVATE_KEY`、`GITHUB_APP_ID`、`GITHUB_INSTALLATION_ID`，没有将密钥值写入源码或记录。

使用实际 `Github` Worker 客户端在本机做只读认证验证通过，检查令牌已撤销。远端 main 为 `595b669ea7f6fde80f1d126e8a717471bc6e0476`；main 尚没有 `.github/workflows/admin-content.yml` 与管理编译器，源码 CI 接入 PR 仍需准备，不能宣称发布全链路已验证。此次未创建内容分支、PR 或激活内容。

密钥注入后的活动 Worker 版本为 `d8d3d7e5-e438-4397-8f63-9f0e5edd0375`（业务代码沿用提交 `7b398ebc57c04b4a738edfcc482d91106db177a9`）。北京时间 2026-10-10 18:39 再次核验页面、JS/CSS 和 API 均跳转正确 Access 团队；公开与预览入口关闭，线上发布登记仍为修订 0，Live 关闭。

隔离的真实无修改编译检查未通过：当前线上 `data_manifest.json` 声明 1,929 个 entries，而线上 `lexicon.json` 实际有 3,564 个。生成器拒绝该计数差异；没有通过修改本地或线上清单来绕过校验。修复应作为可审阅的元数据与源码变更，继续保留现有生效版本。编译器的显式 WeRus User-Agent 已修复合法公开请求的 403，三项 Python 单元测试仍通过。

可复现只读认证：`node scripts/check-github-runtime.mjs 5261281 169892441 <仓库外 PEM 路径>`。可复现隔离编译检查：`python scripts/check-compiler.py`；产物仅保存到忽略的 `.wrangler/compiler-smoke-*`，不上传或发布。

## 发布构建继续接入（2026-10-10）

隔离无修改编译已通过：74 个内容事实组件与线上 SHA-256 完全相同；只有 data_manifest 重新生成计数、覆盖率、assetHashes 和数据版本，并保留原清单来源哈希。清单差异写入 metadata-diff.json，CI 展示差异，管理员确认合并后才进入上传／激活。验证产物没有上传，原线上登记仍为修订 0。

词典正确 canonical 源为已审核的 reviewed-100.json，能够精确复现线上 2026.10.04.cba48c9d3668；旧 pilot-editorial-draft.json 尚未审核，不再用于管理发布。data-source/admin-dictionary/online-reviewed.json 仅登记当前线上事实。data-source/admin-validation/textbook 仅保存已经复现线上 datasetHash 的答案／人工复核依赖；没有复制本地未提交教材正文差异。

源码工作流支持无内容草稿的工具接入 PR，只执行校验；有草稿时要求且只允许一个新增冻结草稿。构建的元数据差异附到 CI 摘要与制品。源码 PR 不自动合并、不激活内容。

发布协调新增进度 CAS、防止迟到回调覆盖取消状态，提交及确认合并前重新验证来源；兼容指针同步失败保留 sync_pending 并自动重试，不重复激活登记。管理台 63 项测试、4 项 Python 测试以及 TypeScript／Vite 构建通过；产品基线与 UR2 校验通过。
