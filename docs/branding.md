# 全员俄人 / WeRus 品牌标识规范 (Brand Identity Specification)

## 1. 核心品牌命名 (Brand Naming)

为统一应用品牌标识并确立正式产品定位，规范命名如下：

| 维度 | 规范命名 | 使用场景 |
| :--- | :--- | :--- |
| **主要中文名称** | **全员俄人** | 应用启动器图标文字（Launcher label）、系统应用列表、设置主标题、日常中文简称 |
| **次要英文品牌** | **WeRus** | 副标题、国际化展示、打包输出前缀、技术/版本标签、代码内高层命名规范 |
| **联合展示名称** | **全员俄人 WeRus** | 应用顶部 Brand Header、Worker 下载页主标题、关于页面、官方文档标题 |

### 禁用格式 (Forbidden Formats)
- ❌ `werus`（全小写）
- ❌ `WeRUS`（全大写后缀）
- ❌ `RusMorph`（除底层兼容包名、历史 Git 仓库名外，严禁在面向用户的界面使用）
- ❌ `全员俄人WeRus`（无空格粘连）
- ❌ `WeRus全员俄人`（颠倒且无空格粘连）

---

## 2. 技术标识与兼容性说明 (Technical Identity)

为了保障历史用户升级、数据库签名及持久化存储的兼容性，**严禁修改底层包名**：

- **`applicationId`**: `org.namchieh.rusmorph`
- **`namespace`**: `org.namchieh.rusmorph`
- **Git 仓库名**: `RusMorph`

底层技术标识保留，仅对外呈现品牌层全面统一。

---

## 3. 打包产物规范 (Build Artifacts)

- **通用 APK**: `WeRus-v<version>-universal.apk`（例如：`WeRus-v0.5.0-universal.apk`）
- **Debug APK**: `WeRus-v<version>-debug-universal.apk`
- **未来架构包**: `WeRus-v<version>-arm64-v8a.apk` / `WeRus-v<version>-x86_64.apk`

---

## 4. 视觉识别与应用图标 (Visual Identity & Launcher Icon)

- **主色调**: `#ed0229`（WeRus 标志红底）
- **文字/前景色**: `#f8e9d4`（米白质感）
- **图标视觉源**: 官方完整设计标识（包含「Ру」、东正教三重洋葱顶十字架与「WeRUS」英文字标）
- **Android 图标安全区适配**:
  - `res/mipmap-anydpi-v26/ic_launcher.xml`：Adaptive Icon（前景透明 PNG + 背景色 `#ed0229`）
  - 图标主体严格约束于内层 66dp/72dp 安全区（Safe Zone），无论在 Android 原生圆形（Pixel/AOSP）、松鼠形（Squircle/OneUI）、或圆角矩形遮罩下，顶部十字架与底部「WeRUS」字标均完美保留，绝不被裁切。
  - 各密度 legacy PNG（mdpi 至 xxxhdpi）与 512px 网页/商店图均经过 4x 超采样圆角/圆形预裁切渲染。

---

## 5. 云端分发 (Cloudflare Worker Distribution)

- **下载服务**: Cloudflare Worker (`app-download-worker`)
- **主 APK 对象 Key**: `WeRus-v0.5.0-universal.apk`
- **兼容 APK 对象 Key**: `werus-latest.apk` / `rusmorph-latest.apk`（向后重定向兼容）
- **下载展示名**: `WeRus-v0.5.0-universal.apk`
- **页面标题**: `全员俄人 WeRus - 正式版 Android 官方下载`
