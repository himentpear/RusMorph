package org.namchieh.rusmorph.wordcard.model

/**
 * 俄语形态学分类。
 */
enum class MorphologyCategory(val labelZh: String, val labelRu: String) {
    NOUN("名词", "Имя существительное"),
    ADJECTIVE("形容词", "Имя прилагательное"),
    PRONOUN("代词", "Местоимение"),
    VERB("动词", "Глагол"),
}

/**
 * 六格语法格位。
 */
enum class RussianCase(val symbol: String, val nameZh: String, val questionZh: String) {
    NOMINATIVE("И", "主格", "谁？什么？"),
    GENITIVE("Р", "属格", "谁的？没有谁？"),
    DATIVE("Д", "与格", "给谁？朝谁？"),
    ACCUSATIVE("В", "宾格", "动作作用于谁/什么？"),
    INSTRUMENTAL("Т", "工具格", "和谁？用什么？"),
    PREPOSITIONAL("П", "前置格", "在哪？关于谁？"),
}

/**
 * 单条规则行（如主格、第一人称等）。
 */
data class MorphologyRuleRow(
    val case: RussianCase? = null,
    val personLabel: String? = null,
    val labelZh: String,
    val questionZh: String? = null,
    val singularEnding: String? = null,
    val pluralEnding: String? = null,
    val singularStemExample: String? = null,
    val pluralStemExample: String? = null,
    val note: String? = null,
)

/**
 * 俄语形态规则数据项。
 */
data class MorphologyRule(
    val id: String,
    val category: MorphologyCategory,
    val endingToken: String,
    val titleZh: String,
    val subtitleRu: String? = null,
    val genderZh: String? = null,
    val summaryZh: String,
    val representativeWord: String,
    val traditionalLabel: String? = null,
    val rows: List<MorphologyRuleRow>,
    val whyExplanation: String? = null,
    val examples: List<String> = emptyList(),
)

/**
 * 代词六格表项。
 */
data class PronounDeclension(
    val pronoun: String,
    val translationZh: String,
    val nominative: String,
    val genitive: String,
    val dative: String,
    val accusative: String,
    val instrumental: String,
    val prepositional: String,
)

/**
 * 静态俄语变格变位规则库。
 * 纯客观语法规则事实，与具体单词数据彻底解耦。
 */
object MorphologyRulesRepository {

    // ==========================================
    // 1. 名词变格规则列表 (10 大常规词尾分类)
    // ==========================================
    val nounRules: List<MorphologyRule> = listOf(
        MorphologyRule(
            id = "noun_masc_hard",
            category = MorphologyCategory.NOUN,
            endingToken = "辅音",
            titleZh = "阳性硬辅音结尾名词",
            subtitleRu = "Мужской род на твёрдый согласный",
            genderZh = "通常为阳性",
            summaryZh = "以硬辅音（-т, -л, -м, -к 等）结尾的无尾阳性名词。",
            representativeWord = "стол",
            traditionalLabel = "传统语法：第二变格",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "—", "-ы / -и", "стол", "стол", "硬辅音后加 -ы；г, к, х, ж, ч, ш, щ 后接 -и"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-а", "-ов / 其他", "стол", "стол", "辅音加 -а；复数通常加 -ов"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-у", "-ам", "стол", "стол", "辅音加 -у；复数加 -ам"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И / Р", "= И / Р", "стол", "стол", "无生命 = 主格；有生命 = 属格"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ом", "-ами", "стол", "стол", "单数加 -ом；复数加 -ами"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ах", "стол", "стол", "单数常接 в/на ... -е；复数加 -ах"),
            ),
            whyExplanation = "阳性硬辅音是最基础的俄语名词形态。宾格单数根据「有生命性」自动分流：无生命物体（如 стол）宾格同主格（В = И），有生命个体（如 студент）宾格同属格（В = Р）。",
            examples = listOf("стол", "студент", "город", "журнал", "парк"),
        ),
        MorphologyRule(
            id = "noun_masc_j",
            category = MorphologyCategory.NOUN,
            endingToken = "-й",
            titleZh = "阳性 -й 结尾名词",
            subtitleRu = "Мужской род на -й",
            genderZh = "通常为阳性",
            summaryZh = "以半元音 -й 结尾的阳性软音名词。",
            representativeWord = "музей",
            traditionalLabel = "传统语法：第二变格（软变化）",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-й", "-и", "музе", "музе"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-я", "-ев", "музе", "музе"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ю", "-ям", "музе", "музе"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И / Р", "= И / Р", "музе", "музе"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ем", "-ями", "музе", "музе"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ях", "музе", "музе"),
            ),
            whyExplanation = "-й 属于软辅音性结尾，其变格词尾与硬辅音完全平行（-а 对应 -я，-у 对应 -ю，-ом 对应 -ем，-ам 对应 -ям）。",
            examples = listOf("музей", "трамвай", "санаторий", "май"),
        ),
        MorphologyRule(
            id = "noun_masc_soft",
            category = MorphologyCategory.NOUN,
            endingToken = "-ь",
            titleZh = "阳性 -ь 结尾名词",
            subtitleRu = "Мужской род на -ь",
            genderZh = "阳性软辅音",
            summaryZh = "以软音符号 -ь 结尾的阳性名词（词尾软化）。",
            representativeWord = "словарь",
            traditionalLabel = "传统语法：第二变格（软变化）",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ь", "-и", "словар", "словар"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-я", "-ей", "словар", "словар"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ю", "-ям", "словар", "словар"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И / Р", "= И / Р", "словар", "словар"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ем", "-ями", "словар", "словар"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ях", "словар", "словар"),
            ),
            whyExplanation = "注意区分以 -ь 结尾的名词词性：阳性词（如 словарь, рубль, преподаватель）属格为 -я，第二变格；阴性词（如 тетрадь）属格为 -и，属于第三变格。",
            examples = listOf("словарь", "рубль", "преподаватель", "день"),
        ),
        MorphologyRule(
            id = "noun_fem_a",
            category = MorphologyCategory.NOUN,
            endingToken = "-а",
            titleZh = "-а 型名词",
            subtitleRu = "1-е склонение (на -а)",
            genderZh = "通常为阴性（包含少数指人阳性，如 папа, дедушка）",
            summaryZh = "俄语中最典型、数量最多的 -а 结尾名词类别（第一变格）。",
            representativeWord = "книга",
            traditionalLabel = "传统语法：第一变格",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-а", "-ы / -и", "книг", "книг", "根据七字母正字法，г, к, х, ж, ч, ш, щ 后接 -и"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-ы / -и", "—", "книг", "книг", "复数属格通常去词尾变成零词尾（книг）"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-е", "-ам", "книг", "книг"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "-у", "= И / Р", "книг", "книг", "单数绝对变化：-а 变为 -у！复数区分有生命"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ой", "-ами", "книг", "книг"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ах", "книг", "книг"),
            ),
            whyExplanation = "在第一变格单数中，词尾 -а 变为 -у（мама → маму, папа → папу, книга → книгу）。指男性的名词（如 папа, дедушка, мужчина）语法性属为阳性，修饰语用阳性（мой папа），但变格词尾完全遵循第一变格。",
            examples = listOf("книга", "мама", "папа", "дедушка", "школа", "газета"),
        ),
        MorphologyRule(
            id = "noun_fem_ya",
            category = MorphologyCategory.NOUN,
            endingToken = "-я",
            titleZh = "-я 型名词",
            subtitleRu = "1-е склонение (на -я)",
            genderZh = "通常为阴性（包含少数指人阳性，如 дядя）",
            summaryZh = "以软元音 -я 结尾的名词（第一变格软变化）。",
            representativeWord = "неделя",
            traditionalLabel = "传统语法：第一变格（软变化）",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-я", "-и", "недел", "недел"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-и", "-ь / -ей", "недел", "недел"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-е", "-ям", "недел", "недел"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "-ю", "= И / Р", "недел", "недел"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ей", "-ями", "недел", "недел"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ях", "недел", "недел"),
            ),
            whyExplanation = "-я 是 -а 的软化对应，宾格单数 -я 变为 -ю（неделя → неделю, дядя → дядю）。指男性的 дядя 为阳性，变格同样遵循本规则。",
            examples = listOf("неделя", "деревня", "дядя", "песня", "земля"),
        ),
        MorphologyRule(
            id = "noun_fem_soft",
            category = MorphologyCategory.NOUN,
            endingToken = "-ь",
            titleZh = "阴性 -ь 结尾名词",
            subtitleRu = "Женский род на -ь",
            genderZh = "阴性",
            summaryZh = "以软音符号结尾的阴性名词（特征：生与前三格全为 -и）。",
            representativeWord = "тетрадь",
            traditionalLabel = "传统语法：第三变格",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ь", "-и", "тетрад", "тетрад"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-и", "-ей", "тетрад", "тетрад"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-и", "-ям", "тетрад", "тетрад"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "-ь", "= И / Р", "тетрад", "тетрад", "单数宾格等于主格 -ь"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ью", "-ями", "тетрад", "тетрад", "工具格单数特征为 -ью"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-и", "-ях", "тетрад", "тетрад", "注意前置格单数是 -и 而非 -е！"),
            ),
            whyExplanation = "第三变格单数三大神定律：属格(Р)、与格(Д)、前置格(П)词尾全部统一是 -и！工具格单数是 -ью。",
            examples = listOf("тетрадь", "ночь", "площадь", "дверь", "мать"),
        ),
        MorphologyRule(
            id = "noun_neut_o",
            category = MorphologyCategory.NOUN,
            endingToken = "-о",
            titleZh = "中性 -о 结尾名词",
            subtitleRu = "Средний род на -о",
            genderZh = "中性硬变化",
            summaryZh = "以硬元音 -о 结尾的中性名词。",
            representativeWord = "окно",
            traditionalLabel = "传统语法：第二变格（中性）",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-о", "-а", "окн", "окн"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-а", "— (脱落元音)", "окн", "окон"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-у", "-ам", "окн", "окн"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И (-о)", "-а", "окн", "окн", "中性绝大多数无生命，宾格永远等于主格"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ом", "-ами", "окн", "окн"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ах", "окн", "окн"),
            ),
            whyExplanation = "中性名词最核心规则：单数与复数中，宾格永远等于主格（В = И）。复数主格变 -а（окно́ → о́кна，重音常移位）。",
            examples = listOf("окно", "слово", "дело", "письмо", "место"),
        ),
        MorphologyRule(
            id = "noun_neut_e",
            category = MorphologyCategory.NOUN,
            endingToken = "-е",
            titleZh = "中性 -е 结尾名词",
            subtitleRu = "Средний род на -е",
            genderZh = "中性软变化",
            summaryZh = "以元音 -е 结尾的中性软音名词。",
            representativeWord = "море",
            traditionalLabel = "传统语法：第二变格（中性软）",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-е", "-я", "мор", "мор"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-я", "-ей", "мор", "мор"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ю", "-ям", "мор", "мор"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И (-е)", "-я", "мор", "мор"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ем", "-ями", "мор", "мор"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-е", "-ях", "мор", "мор"),
            ),
            whyExplanation = "-е 是中性 -о 的软音对应，复数主格变成 -я（море → моря）。",
            examples = listOf("море", "поле", "сердце", "солнце"),
        ),
        MorphologyRule(
            id = "noun_fem_iya",
            category = MorphologyCategory.NOUN,
            endingToken = "-ия",
            titleZh = "-ия 特殊阴性名词",
            subtitleRu = "Женский род на -ия",
            genderZh = "阴性特殊前置格",
            summaryZh = "以 -ия 结尾的名词（国名、抽象名词极常见）。",
            representativeWord = "Россия",
            traditionalLabel = "第一变格特殊前置格 -ии",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ия", "-ии", "Росси", "Росси"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-ии", "-ий", "Росси", "Росси"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ии", "-иям", "Росси", "Росси"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "-ию", "-ии", "Росси", "Росси"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ией", "-иями", "Росси", "Росси"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-ии", "-иях", "Росси", "Росси", "★ 关键考点：前置格是 -ии 而非 -е！"),
            ),
            whyExplanation = "初学者极容易写错：普通阴性前置格是 в школ-е，但 -ия 结尾名词前置格绝对是 в Росси-и, в аудитори-и, об истори-и！",
            examples = listOf("Россия", "аудитория", "история", "линия", "станция"),
        ),
        MorphologyRule(
            id = "noun_neut_ie",
            category = MorphologyCategory.NOUN,
            endingToken = "-ие",
            titleZh = "-ие 特殊中性名词",
            subtitleRu = "Средний род на -ие",
            genderZh = "中性特殊前置格",
            summaryZh = "以 -ие 结尾的抽象与动名词（建筑、练习等）。",
            representativeWord = "здание",
            traditionalLabel = "第二变格特殊前置格 -ии",
            rows = listOf(
                MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ие", "-ия", "здани", "здани"),
                MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-ия", "-ий", "здани", "здани"),
                MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ию", "-иям", "здани", "здани"),
                MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "-ие", "-ия", "здани", "здани"),
                MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ием", "-иями", "здани", "здани"),
                MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-ии", "-иях", "здани", "здани", "★ 关键考点：前置格是 -ии 而非 -е！"),
            ),
            whyExplanation = "同 -ия 一样，-ие 结尾中性名词在前置格单数中必须写作 -ии（в здани-и, в упражнени-и, в общежи́ти-и）。",
            examples = listOf("здание", "общежитие", "упражнение", "собрание", "внимание"),
        ),
    )

    // ==========================================
    // 2. 形容词变格规则
    // ==========================================
    val adjectiveHardRows: List<MorphologyRuleRow> = listOf(
        MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ый / -ой", "-ые", note = "阳: -ый/ой | 阴: -ая | 中: -ое | 复: -ые"),
        MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-ого (发-ова)", "-ых", note = "阳/中: -ого | 阴: -ой | 复: -ых"),
        MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ому", "-ым", note = "阳/中: -ому | 阴: -ой | 复: -ым"),
        MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И / Р", "= И / Р", note = "阳性/复数依有生命性分流；阴性单数固定为 -ую"),
        MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-ым", "-ыми", note = "阳/中: -ым | 阴: -ой (-ою) | 复: -ыми"),
        MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-ом", "-ых", note = "阳/中: -ом | 阴: -ой | 复: -ых"),
    )

    val adjectiveSoftRows: List<MorphologyRuleRow> = listOf(
        MorphologyRuleRow(RussianCase.NOMINATIVE, null, "И 主格", "谁？什么？", "-ий", "-ие", note = "阳: -ий | 阴: -яя | 中: -ее | 复: -ие"),
        MorphologyRuleRow(RussianCase.GENITIVE, null, "Р 属格", "谁的？没有谁？", "-его (发-ева)", "-их", note = "阳/中: -его | 阴: -ей | 复: -их"),
        MorphologyRuleRow(RussianCase.DATIVE, null, "Д 与格", "给谁？朝谁？", "-ему", "-им", note = "阳/中: -ему | 阴: -ей | 复: -им"),
        MorphologyRuleRow(RussianCase.ACCUSATIVE, null, "В 宾格", "动作作用于谁？", "= И / Р", "= И / Р", note = "阳性/复数依有生命性分流；阴性单数固定为 -юю"),
        MorphologyRuleRow(RussianCase.INSTRUMENTAL, null, "Т 工具格", "和谁？用什么？", "-им", "-ими", note = "阳/中: -им | 阴: -ей | 复: -ими"),
        MorphologyRuleRow(RussianCase.PREPOSITIONAL, null, "П 前置格", "在哪？关于谁？", "-ем", "-их", note = "阳/中: -ем | 阴: -ей | 复: -их"),
    )

    // ==========================================
    // 3. 代词六格数据
    // ==========================================
    val personalPronouns: List<PronounDeclension> = listOf(
        PronounDeclension("я", "我", "я", "меня́", "мне", "меня́", "мной (мно́ю)", "обо мне"),
        PronounDeclension("ты", "你", "ты", "тебя́", "тебе́", "тебя́", "тобо́й (тобо́ю)", "о тебе́"),
        PronounDeclension("он / оно", "他 / 它", "он / оно́", "его́ (него́)", "ему́ (нему́)", "его́ (него́)", "им (ним)", "о нём"),
        PronounDeclension("она", "她", "она́", "её (неё)", "ей (ней)", "её (неё)", "ей / е́ю (ней)", "о ней"),
        PronounDeclension("мы", "我们", "мы", "нас", "нам", "нас", "на́ми", "о нас"),
        PronounDeclension("вы", "你们 / 您", "вы", "вас", "вам", "вас", "ва́ми", "о вас"),
        PronounDeclension("они", "他们 / 它们", "они́", "их (них)", "им (ним)", "их (них)", "и́ми (ни́ми)", "о них"),
    )

    // ==========================================
    // 4. 动词变位对照与规则
    // ==========================================
    data class ConjugationComparisonRow(
        val person: String,
        val firstEnding: String,
        val secondEnding: String,
        val firstExample: String,
        val secondExample: String,
    )

    val conjugationComparison: List<ConjugationComparisonRow> = listOf(
        ConjugationComparisonRow("я (我)", "-у / -ю", "-у / -ю", "чита́ю", "говорю́"),
        ConjugationComparisonRow("ты (你)", "-ешь", "-ишь", "чита́ешь", "говори́шь"),
        ConjugationComparisonRow("он / она (他/她)", "-ет", "-ит", "чита́ет", "говори́т"),
        ConjugationComparisonRow("мы (我们)", "-ем", "-им", "чита́ем", "говори́м"),
        ConjugationComparisonRow("вы (你们/您)", "-ете", "-ите", "чита́ете", "говори́те"),
        ConjugationComparisonRow("они (他们)", "-ут / -ют", "-ат / -ят", "чита́ют", "говоря́т"),
    )

    val pastTenseRows: List<MorphologyRuleRow> = listOf(
        MorphologyRuleRow(null, null, "♂ 阳性单数", "主语为阳性", "-л", null, "зна", null, "знал, читал, говорил"),
        MorphologyRuleRow(null, null, "♀ 阴性单数", "主语为阴性", "-ла", null, "зна", null, "знала, читала, говорила"),
        MorphologyRuleRow(null, null, "○ 中性单数", "主语为中性", "-ло", null, "зна", null, "знало, читало, говорило"),
        MorphologyRuleRow(null, null, "◎ 复数形式", "所有复数主语", "-ли", null, "зна", null, "знали, читали, говорили"),
    )
}
