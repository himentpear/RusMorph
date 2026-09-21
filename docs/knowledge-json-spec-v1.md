# RusMorph Knowledge JSON 生产规范 (v1.0)

> **目标**：建立“人工标注软件 → 质检审核流 → RusMorph 核心学习引擎与本地数据库”的稳定交互协议与工程质量标准。  
> **适用对象**：标注工具开发团队、俄语语言学标注员、审校专家、RusMorph 数据资产管线维护人员。  
> **版本**：v1.0.0  
> **状态**：ACTIVE / SPECIFICATION

---

## 1. 规范总览与数据流转

### 1.1 数据链路架构

```
[原始教材/课文语料]
       │ (分句分词与切分)
       ▼
[人工标注软件 (Annotation Workbench)] ◄─── [AI 预标注辅助]
       │
       ▼ (导出 Knowledge JSON / reviewStatus="draft")
[专家审校 / 质检流程 (Quality Assurance)]
       │
       ▼ (reviewStatus="approved")
[RusMorph 数据导入管线 (textbook_importer / xlsx_builder)]
       │
       ▼ (生成 Room 资产 / 知识图谱索引)
[RusMorph 客户端 (词典 / 智能学习 / AI卡片组装)]
```

### 1.2 设计原则
1. **单一数据真相 (Single Source of Truth)**：标注数据以标准 JSON 结构持久化，字段语义明确，无歧义。
2. **文本偏移强对齐 (Strict Character Offsets)**：所有标注均基于所属句子（`sentenceId`）的原始俄语文本 UTF-16/Unicode 字符坐标进行闭包切片，保证高亮与交互零偏移。
3. **向下兼容与向前演进 (Compatibility)**：
   - 核心层维持与 RusMorph 客户端 Room 数据库（`sentence_knowledge` 表）扁平字段的直接映射。
   - 扩展层提供强类型的语义载荷（`payload`），为下游 AI 对话生成、智能替换练习提供结构化能力。

---

## 2. Knowledge Schema v1 规范定义

### 2.1 顶级实体结构 (Knowledge Object)

每个知识标注项表示句子中的一个语言学实体节点：

```typescript
interface KnowledgeItem {
  /** 唯一标识符，格式：{sentenceId}-k{index} 或 UUID */
  id: string;

  /** 所属句子全局唯一 ID（如 ur2-lesson-1-b1-s1） */
  sentenceId: string;

  /** 知识实体类型 */
  type: "WORD" | "PHRASE" | "GRAMMAR" | "PATTERN";

  /** 句子中被标注的高亮文本片段（与 sourceText[start:end] 严格一致） */
  text: string;

  /** 中文标签/标题/简明释义（UI 卡片标题或悬浮提示） */
  label: string;

  /** 详细解析/语法说明/用法阐述（支持 Markdown） */
  explanation?: string | null;

  /** 典型例句（俄语原文 + 中文对照），若无则为 null */
  example?: string | null;

  /** 字符切片起始下标（含，从 0 计数） */
  start: number;

  /** 字符切片结束下标（不含，start < end <= len(sentence)） */
  end: number;

  /** 数据来源 */
  source: "human" | "ai" | "imported";

  /** 审核状态 */
  reviewStatus: "draft" | "reviewed" | "approved";

  /** 规范版本号（当前固定为 1） */
  version: number;

  /** 类型特化扩展载荷（满足高级标注要求） */
  payload?: WordPayload | PhrasePayload | GrammarPayload | PatternPayload;
}
```

### 2.2 枚举约束与状态机

#### `source` (数据源)
- `human`: 人工专家或标注员手动划定标注。
- `ai`: 大模型或规则引擎自动化初标（需进入 review 流）。
- `imported`: 历史沉淀语料、词典库或第三方课件直接导入。

#### `reviewStatus` (审核状态流转)
```
  [ai / imported] ──► draft (草稿)
                        │
                        ▼ (初审员校验)
                     reviewed (已初审)
                        │
                        ▼ (专家终审通过)
                     approved (已准入 / 可打包入库)
```
- 只有标记为 `approved` 的数据包才允许被打包进发布级 Android Assets 数据库。

---

## 3. 四大核心类型标注质量标准

### 3.1 WORD（重点生词 / 核心变位变格词）

用于定位句中的核心词汇、非常规变位/变格形式，帮助学习者掌握词汇原型与语法特征。

#### 质量合格标准：
1. **原词 (`text`)**：必须保留原句中的变体形式（包括重音符号 `\u0301`，若课文带重音）。
2. **词元 (`lemma`)**：动词采用未完成体不定式；名词采用第一格单数；形容词采用阳性单数第一格。
3. **词性 (`pos`)**：采用标准词性代号（`NOUN`, `VERB`, `ADJ`, `ADV`, `PREP`, `CONJ`, `PRON`, `NUM`, `PARTICLE`）。
4. **语法特征 (`grammarFeatures`)**：精确标明原形在句子中所处形态（如：动词的体、时态、人称、数；名词的格、数等）。
5. **精准释义 (`label` & `definition`)**：优先贴合当前上下文语境的中文对译，兼顾常用基础义项。

#### Schema 规范：
```json
{
  "id": "ur2-lesson-1-b1-s1-k1",
  "sentenceId": "ur2-lesson-1-b1-s1",
  "type": "WORD",
  "text": "изучал",
  "label": "学习；研究",
  "explanation": "动词 изучать（未完成体）过去时阳性形式，强调过去进行的学习或钻研动作。",
  "example": "Он долго изучал русский язык.",
  "start": 12,
  "end": 18,
  "source": "human",
  "reviewStatus": "approved",
  "version": 1,
  "payload": {
    "lemma": "изучать",
    "pos": "VERB",
    "aspect": "imperfective",
    "tense": "past",
    "gender": "masculine",
    "number": "singular",
    "collocations": ["изучать грамматику", "изучать культуру"]
  }
}
```

---

### 3.2 PHRASE（固定搭配 / 习语表达 / 词组）

用于标示多词构成的固定词组、成语习语、特定介词搭配，整体理解优于单字拆解。

#### 质量合格标准：
1. **固定表达范围切分**：标注切片必须完整覆盖整个固定搭配，不能漏标关键伴随虚词（如介词、连接词）。
2. **中文解释**：提供标准且通顺的对应中文翻译，避免生硬直译。
3. **使用范围 (`usageScope`)**：明确标注其语体色彩与适用语境（如：口语 `spoken`、书面语 `written`、公文公函 `formal`、日常客套 `daily` 等）。
4. **搭配规律 (`collocationRules`)**：若后续带特定格支配或接格要求，应明确提示。

#### Schema 规范：
```json
{
  "id": "ur2-lesson-1-b2-s3-k1",
  "sentenceId": "ur2-lesson-1-b2-s3",
  "type": "PHRASE",
  "text": "С де́тства",
  "label": "从小；自童年起",
  "explanation": "固定时间状语表达，由介词 с + 中性名词 детство 第二格构成，表示某种状态或习惯自幼时开始。",
  "example": "С детства я люблю читать книги.",
  "start": 0,
  "end": 10,
  "source": "human",
  "reviewStatus": "approved",
  "version": 1,
  "payload": {
    "phraseType": "COLLOCATION",
    "usageScope": "通用/日常交流及叙述性文学",
    "register": "neutral",
    "synonyms": ["с ранних лет", "с малых лет"]
  }
}
```

---

### 3.3 GRAMMAR（核心语法点 / 句法结构）

用于句中体现的关键语法现象，侧重规则解释、句法接格关系与形态句法特征。

#### 质量合格标准：
1. **结构公式 (`structure`)**：使用通用的语言学公式描述，如：`动词 + творительный падеж`，`несмотря на + винительный падеж`。
2. **语法点明确 (`grammarPoint`)**：清晰界定语法点类别（如：动词接格、副动词短语做状语、条件从句、体的选择等）。
3. **规则详述 (`explanation`)**：阐述语法规则的内在逻辑与易错点（如“为什么这里必须用未完成体”或“为什么支配第五格而不是第四格”）。
4. **对比与例句 (`example`)**：提供至少 1 句语法规范的独立例句，帮助举一反三。

#### Schema 规范：
```json
{
  "id": "ur2-lesson-1-b2-s3-k2",
  "sentenceId": "ur2-lesson-1-b2-s3",
  "type": "GRAMMAR",
  "text": "интересова́лся иностра́нными языка́ми",
  "label": "动词支配第五格（工具格）",
  "explanation": "动词 интересоваться 要求后接名词第五格（творительный падеж），表示对某事物产生兴趣或钻研，不能直接接第四格。",
  "example": "Мой брат интересуется современной музыкой.",
  "start": 13,
  "end": 50,
  "source": "human",
  "reviewStatus": "approved",
  "version": 1,
  "payload": {
    "structure": "интересоваться + Творительный падеж (Т.п.)",
    "grammarPoint": "动词反身代词接格关系",
    "applicableRules": [
      "动词为中动态反身形式",
      "所感兴趣的对象必须采用第五格形态"
    ]
  }
}
```

---

### 3.4 PATTERN（高阶表达模式 / 交际句型）

**要求最高**：因为 PATTERN 直接决定学习者的**口语与写作输出能力（Productive Language Ability）**，也是 RusMorph AI 智能导师生成替换练习、生成对话任务的核心依据。

#### 质量合格标准（必须同时满足以下三项）：
1. **交际场景 (`scene`)**：
   - 标注必须标明该句型生效的具体交际功能与现实生活场景（如：“初次见面自我介绍”、“餐厅点餐”、“问询路线”、“征求许可”等）。
2. **替换槽位 (`slots`)**：
   - 必须将句型解构为**常量支架（Frame）**与**变量槽位（Slots）**。
   - 每一个槽位必须定义：
     - `name`：槽位英文标识符（如 `name`, `age`, `city`, `verb_inf`）。
     - `label`：槽位中文说明（如：“姓名”、“年龄数值”、“地点名词”）。
     - `constraint`：语法约束（如：`Nominative Case (人名一格)`, `Genitive Case (数量第二格)`）。
     - `options`：推荐的替换候选词列表（至少提供 2 个典型词，供下游生成器使用）。
3. **典型示范 (`example` & `dialogueExamples`)**：
   - 给出完整的套用示例及对话上下文示范。

#### Schema 规范：
```json
{
  "id": "ur2-lesson-1-b1-s1-k2",
  "sentenceId": "ur2-lesson-1-b1-s1",
  "type": "PATTERN",
  "text": "Меня́ зову́т Васи́лий Никола́евич",
  "label": "姓名介绍模式",
  "explanation": "用于正式或非正式场合的自我介绍。句型骨架为：Меня зовут + [Имя / Имя и Отчество]（人名第一格）。",
  "example": "Здравствуйте! Меня зовут Антон.",
  "start": 0,
  "end": 30,
  "source": "human",
  "reviewStatus": "approved",
  "version": 1,
  "payload": {
    "scene": "社交结识 / 自我介绍",
    "patternFormula": "Меня зовут + <name>",
    "register": "通用（正式/非正式均可）",
    "slots": [
      {
        "slot": "name",
        "label": "姓名",
        "constraint": "人名第一格（Именительный падеж）",
        "description": "可以单填名字（Анна）、全名（Иван Петров）或名+父称（Василий Николаевич）",
        "options": ["Антон", "Анна", "Сергей Иванович", "Елена"]
      }
    ],
    "dialogueExamples": [
      {
        "speakerA": "Как вас зовут?",
        "speakerB": "Меня зовут Анна."
      }
    ]
  }
}
```

---

## 4. 边界裁决与多义判定指南 (Disambiguation Guide)

在实际人工标注中，经常出现类型交叠与边界争议，统一裁决标准如下：

| 场景冲突 | 判定原则 | 规范定型 |
| :--- | :--- | :--- |
| **WORD vs PHRASE** | 若由两个及以上词组合（含固定虚词搭配），且组合后产生了单一单词无法独立表达的特定语用或整体语义（如 `всю жизнь`, `с детства`），一律标为 **PHRASE**。单个变位词（如 `изучал`）标为 **WORD**。 | 组合义优先归为 `PHRASE` |
| **PHRASE vs PATTERN** | `PHRASE` 整体是**封闭固定的词组**，内部没有可自由置换的槽位（如 `ни пуха ни пера`, `в конце концов`）；而 `PATTERN` 是**开放式句型支架**，带有明确的槽位可供填入不同实体（如 `Меня зовут + [имя]`, `Я родился в + [место]`）。 | 带有开放替换槽位的归为 `PATTERN` |
| **GRAMMAR vs PATTERN** | `GRAMMAR` 强调**语法规则、变格控制与形态逻辑**（如 `支配第五格`、`双重未完成体否定`）；`PATTERN` 强调**交际功能与情境会话输出**（如 `委婉拒绝`、`向他人致谢`）。若同一处文本既有语法又有交际模式，允许针对不同切片并存标注，或分别创建独立 Annotation 节点。 | 语法规则归 `GRAMMAR`，交际句式归 `PATTERN` |

---

## 5. 校验规则与工程验收准则 (QA Checklist)

标注工具在导出 JSON 以及 RusMorph 导入数据时，必须通过以下自动校验（Linters）：

1. **文本切片边界强校验**：
   - 必须满足：`0 <= start < end <= len(sentence.sourceText)`。
   - 必须满足：`sentence.sourceText[start:end] == knowledge.text`（去除首尾空白后保持精准一致）。
2. **唯一性校验**：
   - 全局 `id` 唯一，不可重复。
   - 同一句内相同 `type` 与相同 `start, end` 的记录不允许重复。
3. **必填字段校验**：
   - `id`, `sentenceId`, `type`, `text`, `label`, `start`, `end`, `source`, `reviewStatus`, `version` 不能为空。
   - 当 `reviewStatus == "approved"` 时，`label` 与 `explanation` 必须为有效非空文本。
4. **PATTERN 特项合规校验**：
   - 若 `type == "PATTERN"`，`payload.scene` 不能为空，`payload.slots` 数组长度必须 `>= 1`，且每个 slot 必须具有有效的 `slot` 与 `constraint`。

---

## 6. 与 RusMorph 工程管线的对接机制

1. **向后兼容性**：
   - 现有的 `TextbookAssetImporter.kt` 与 `import_textbooks.py` 可直接无缝解析顶级字段（`id`, `sentenceId`, `type`, `text`, `label`, `explanation`, `example`, `start`, `end`, `source`, `reviewStatus`, `version`）。
   - `payload` 为可选扩展对象，在导入 Room 数据库时可选择序列化为 JSON 字符串保存至扩展字段，或由 Python 端构建工具提取以生成多轮 AI Prompt 与训练样本。
2. **导入脚本映射**：
   - `source` 对应数据库的 `generatedBy` 字段。
   - `version` 对应数据库的 `knowledgeVersion` 字段。
   - 数据流完全兼容现有单元测试 `KnowledgeImportTest.kt`。
