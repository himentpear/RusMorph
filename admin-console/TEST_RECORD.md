# 自动化检查记录

日期：2026-10-10。唯一构建目录：`C:\rusmorph-v006`。核对到版本 0.008 / versionCode 8，分支 `codex/v008-download-deploy`，任务起点 `e80e9c0`。已有修改保留；本记录不代表真机或生产登录验收通过。

| 检查 | 实际结果 |
| --- | --- |
| 管理台 Vitest | 60 项通过，包括鉴权、同源、会员事务、幂等、修订、资源、发布失败与回滚 |
| 管理编译器 Python unittest | 3 项通过，包括词汇关系重绑和私有源不能偷偷改变线上基线 |
| agent-proxy | 162 项通过，TypeScript 通过 |
| werus-ai / 既有 Android SSE | 63 项通过，TypeScript 和 Wrangler dry-run 通过 |
| 小程序 | 373 项通过 |
| 官方模板编译 | miniprogram-compiler 0.2.3 的完整 WXML / WXSS 编译通过 |
| 产品与教材 | 产品基线通过；UR2 校验无错误或无效 offset |
| 主包源码估算 | 1,889,136 字节，1.802 MiB，剩余 208,016 字节；最终上传包体由微信开发者工具核对 |
| 管理台构建 | TypeScript + Vite 通过；JS 268.21 kB，CSS 19.09 kB |
| workerd 运行时 | JWT 页面、资源、API 鉴权与同源拒绝通过 |
| 依赖审计 | 前一轮 npm audit 为 0 个漏洞 |

可复现管理检查：

```powershell
Set-Location -LiteralPath 'C:\rusmorph-v006\admin-console'
npm ci
npm test
npm run build
python -m unittest discover -s test -p compiler_test.py
npx wrangler deploy --dry-run --outdir .wrangler/smoke
node test/runtime-smoke.mjs
```

复现模板编译，可在 `.wrangler/template-check` 临时安装 `miniprogram-compiler@0.2.3`，再运行 `node scripts/check-miniprogram.cjs .wrangler/template-check/node_modules/miniprogram-compiler`。测试文件、签名密钥和模拟库不会进入生产 bundle。

线上原有 279 个不可变资源已读回校验后登记；未上传本机内容，未激活新教材或词典版本。线上来源提交缺失，登记值保留 null。

尚待完成：Access 应用与真实登录、GitHub App 最小安装权限、受保护源码 PR 与远端 CI 全链路、完整后端部署、桌面视觉复核，以及计划中的结构新增编辑流程。当前编辑器可修改现有稳定 ID；新增记录尚需审核源码迁移，不能宣称完整内容发布功能已交付。

首次独立 Worker 部署仍拒绝所有未通过 Access 验证的请求。Live 关闭。测试没有部署小程序或发布 APK。

2026-10-10 Access 启用检查：管理台 56 项测试、TypeScript/Vite 构建、产品基线及 UR2 教材校验再次通过。线上 `/`、`/api/admin/v1/me` 和当前 JS/CSS 均 302 至正确 Access 团队，邮箱验证码登录页 200；未触发发送验证码。公开 Worker 与预览入口仍关闭，登记内容修订为 0，Live 仍关闭。真实管理员登录尚待本人验收；GitHub App 和安装权限仍为空。


发布恢复补充：新增回滚领取后 D1 任务创建失败、连续数据库故障、待恢复草稿修订冲突及暂停任务不自动重跑四项测试，合计 60 项通过；TypeScript/Vite 构建通过。回滚创建先持久记录意图，D1 未恢复时继续排期；已失效草稿释放原领取权，不改变线上版本。

GitHub App 最小权限、单仓库安装与实际 Worker 客户端只读认证通过，三项服务端 secrets 已配置。真实无修改编译 smoke 已运行，因线上 data_manifest entries=1929、lexicon 实际条数=3564 而失败；该失败不计为通过，原内容保持生效。远端 main 缺少 admin-content.yml 和管理编译器，CI/源码接入仍未完成。编译器显式请求标识修复后公开读取成功，三项 Python unittest 通过。

2026-10-10 继续实施：管理台 63 tests 通过，compiler 4 tests 通过，TypeScript／Vite 构建通过；产品基线与 UR2 数据集校验通过。复用已下载但逐件重新比对在线目录哈希的隔离 smoke 通过：74 个事实组件字节不变，生成清单元数据有显式差异报告，无上传或激活。已审核 100 词 canonical 使用原发布日期重建，与线上词典版本一致。新增测试覆盖迟到进度更新、来源版本推进及 R2 指针同步失败后的幂等恢复。
