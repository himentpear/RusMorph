# WeRus v0.006 开发与部署约定

## 唯一构建基线

- WeRus v0.006 的工作与构建目录是 `C:\rusmorph-v006`。当前教材接入分支是 `codex/v006-textbook-bridge`，界面基线来自 `cf3c750`。分支会随任务变化，不能凭分支名推断版本；每次都核对 `app/build.gradle.kts` 和 `release/ui-baseline.json`。
- `D:\Folder\本研\APP` 的 `refactor/phase1-structure` 是旧版历史工作树，只能读取参考。不得从 D 构建、安装或交付标为 WeRus v0.006 的 APK；不得将旧导航、主题、Room schema 或 Importer 整体移入 C。
- 进入任务先执行：

```powershell
Set-Location -LiteralPath 'C:\rusmorph-v006'
Get-Location
git branch --show-current
git rev-parse --short HEAD
git status --short
```

保留现有未提交修改。只暂存本任务文件；预览包若包含未提交修改，交付时逐项说明。正式发布必须有可追溯的已提交源码状态。

## 构建前核对

1. 运行 `python tools/verify_product_contract.py`。若失败，检查实际界面与批准的 v0.006 基线；不得为了通过校验而盲目改写基线文件。
2. 运行 `python tools/textbook_dataset/validate_dataset.py app/src/main/assets/database/textbook/university_russian_2`，确认教材事实层的计数、关系和 manifest 哈希。部署任务不重写教材 JSON、Importer 或 Room schema。
3. 核对 `app/build.gradle.kts` 的 `applicationId`、`versionCode`、`versionName`，以及 `git status --short`。同包名覆盖安装需要更高的 versionCode 和相同签名证书。

## 本地预览包

```powershell
.\gradlew.bat :app:assembleLocalDebug
```

产物位于 `app\build\outputs\apk\local\debug\app-local-debug.apk`。当前工作树的 `localDebug` 包名为 `org.namchieh.rusmorph.preview`，版本名带 `-preview`，使用 Android Debug 签名。`.preview` 后缀目前来自尚未提交的 `app/build.gradle.kts` 修改；在其他 checkout 中必须重新核对 APK 元数据，不能仅凭本文件假定包名。该包与正式应用并存，只能称为预览包，不能作为正式版覆盖更新包。需要安装到已连接设备时使用 `adb install -r <预览包绝对路径>`。

## 正式签名包与发布

正式包使用 `:app:assembleProductionRelease`，包名为 `org.namchieh.rusmorph`。构建前通过安全的本地环境变量或 Gradle 属性提供以下四项，密钥和口令不得写入仓库、命令输出或交付说明：

- `RUSMORPH_RELEASE_STORE_FILE`
- `RUSMORPH_RELEASE_STORE_PASSWORD`
- `RUSMORPH_RELEASE_KEY_ALIAS`
- `RUSMORPH_RELEASE_KEY_PASSWORD`

```powershell
.\gradlew.bat :app:assembleProductionRelease
```

签名未配置时构建应失败，不能把 Debug 签名包改名为正式包。正式 APK 位于 `app\build\outputs\apk\production\release\`。交付前用 Android SDK 的 `aapt dump badging` 核对包名与版本，用 `apksigner verify --print-certs` 核对签名，再计算 SHA-256，并确认 APK 内包含预期教材资产。记录实际路径、分支、提交、构建变体、包名、版本和签名证书摘要。

GitHub 的 `.github/workflows/android-release.yml` 在推送 `v*` 标签后构建并发布正式 APK、SHA-256 文件和 `release/android-stable.json`。标签必须是 `v` 加当前 `versionName`；更新清单由 `tools/generate_update_manifest.py` 生成。只有用户明确要求发布到 GitHub 时才推送标签。工作流所需的 keystore 与口令由 GitHub Secrets 提供。

## 安装兼容性

旧 D 工作树曾产出 versionCode 51、Debug 签名的同包名 APK。当前 v0.006 正式包的 versionCode 6，签名也可能不同，因此不能假定可覆盖安装。不要为排除安装冲突直接卸载用户应用；先核对现有包的版本和证书，并保留设备数据。预览包的 `.preview` 包名可避免覆盖正式应用。
