# WeRus v0.006 教材接入 checkpoint

- 基线：`C:\rusmorph-v006`，`codex/v006-textbook-bridge`，起点 `cf3c750`。
- 数据来源：旧工作树提交 `a70d90d` 与 `7597656` 中已验证的《大学俄语（二）》事实层和语法关联状态；资产哈希 `f952fba7cfb9d128542dcc9ae365343280e2a815648d3e10dab788fc3d70cfed`。
- 接入方式：v0.006 的 `CourseRepository` 从原始教材资产读取第二册课文；保留 v0.006 的 Room v7、Importer、主题和导航。
- 可显示：12 课、102 段、378 句课文。第二册对话章节有目录记录，但 0 个对应对话段落，因此不显示空对话入口。
- 待补数据：句级翻译 0；speaker 绑定 0；34 个语法关联尚无正式解析结果。`显示翻译`会对缺失句子显示“暂无译文”。
- 预览构建：`assembleLocalDebug`，`org.namchieh.rusmorph.preview`，versionCode 6，versionName `0.006-preview`，Android Debug 签名。它可与正式包并存，不是生产更新包。
- 该预览包还包含 C 工作树原有的未提交壁纸、导航和 `local` flavor 改动；这些用户改动未纳入本教材 checkpoint。重建同一预览包时需保留相同工作树状态。

交付前运行 `tools/verify_product_contract.py` 和 `tools/textbook_dataset/validate_dataset.py`，核对 APK 中的资产、包名、版本与签名。
