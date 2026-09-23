# WeRus v0.006 工作基线

本工作树是 WeRus v0.006 的实现与 APK 构建基线。开始修改前核对工作路径、分支、`versionName` 和 `release/ui-baseline.json`。本次教材接入位于 `codex/v006-textbook-bridge`，以 `cf3c750` 的 v0.006 界面为起点。

`D:\Folder\本研\APP` 中的 `refactor/phase1-structure` 仅作历史数据和代码参考。不要从该目录编译或交付标为 WeRus v0.006 的 APK。旧目录中的教材事实层只允许按需复制到本工作树，不要将旧导航、主题、Room schema 或 Importer 整体移植。

构建交付前运行 `python tools/verify_product_contract.py`、`python tools/textbook_dataset/validate_dataset.py app/src/main/assets/database/textbook/university_russian_2`，并核对 APK 的包名、版本、签名和输出路径。保留用户已有的未提交修改；只暂存本任务文件。
