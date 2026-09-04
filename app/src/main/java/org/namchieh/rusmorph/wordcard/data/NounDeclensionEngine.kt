package org.namchieh.rusmorph.wordcard.data

import org.namchieh.rusmorph.wordcard.model.Gender
import org.namchieh.rusmorph.wordcard.model.Lexeme

/**
 * 俄语名词六格单复数形态派生引擎。
 * 遵循现代俄语形态学核心定律（三大变格法、七字母规则、五字母规则、有生命性分流及高频不规则词表）。
 */
object NounDeclensionEngine {

    private val SEVEN_LETTERS = setOf('г', 'к', 'х', 'ж', 'ч', 'ш', 'щ')
    private val FIVE_LETTERS = setOf('ж', 'ч', 'ш', 'щ', 'ц')
    private val SIBILANTS = setOf('ж', 'ч', 'ш', 'щ')

    // 常见不规则/异根名词显式字典表
    private val IRREGULAR_NOUNS: Map<String, Pair<Lexeme.CaseForms, Lexeme.CaseForms>> = mapOf(
        "человек" to Pair(
            Lexeme.CaseForms("человек", "человека", "человеку", "человека", "человеком", "человеке"),
            Lexeme.CaseForms("люди", "людей", "людям", "людей", "людьми", "людях")
        ),
        "ребёнок" to Pair(
            Lexeme.CaseForms("ребёнок", "ребёнка", "ребёнку", "ребёнка", "ребёнком", "ребёнке"),
            Lexeme.CaseForms("дети", "детей", "детям", "детей", "детьми", "детях")
        ),
        "ребенок" to Pair(
            Lexeme.CaseForms("ребенок", "ребенка", "ребенку", "ребенка", "ребенком", "ребенке"),
            Lexeme.CaseForms("дети", "детей", "детям", "детей", "детьми", "детях")
        ),
        "друг" to Pair(
            Lexeme.CaseForms("друг", "друга", "другу", "друга", "другом", "друге"),
            Lexeme.CaseForms("друзья", "друзей", "друзьям", "друзей", "друзьями", "друзьях")
        ),
        "брат" to Pair(
            Lexeme.CaseForms("брат", "брата", "брату", "брата", "братом", "брате"),
            Lexeme.CaseForms("братья", "братьев", "братьям", "братьев", "братьями", "братьях")
        ),
        "сын" to Pair(
            Lexeme.CaseForms("сын", "сына", "сыну", "сына", "сыном", "сыне"),
            Lexeme.CaseForms("сыновья", "сыновей", "сыновьям", "сыновей", "сыновьями", "сыновьях")
        ),
        "мать" to Pair(
            Lexeme.CaseForms("мать", "матери", "матери", "мать", "матерью", "матери"),
            Lexeme.CaseForms("матери", "матерей", "матерям", "матерей", "матерями", "матерях")
        ),
        "дочь" to Pair(
            Lexeme.CaseForms("дочь", "дочери", "дочери", "дочь", "доcherью".replace("cher", "ер"), "дочери"),
            Lexeme.CaseForms("дочери", "дочерей", "дочерям", "дочерей", "дочерями", "дочерях")
        ),
        "время" to Pair(
            Lexeme.CaseForms("время", "времени", "времени", "время", "временем", "времени"),
            Lexeme.CaseForms("времена", "времён", "временам", "времена", "временами", "временах")
        ),
        "имя" to Pair(
            Lexeme.CaseForms("имя", "имени", "имени", "имя", "именем", "имени"),
            Lexeme.CaseForms("имена", "имён", "именам", "имена", "именами", "именах")
        ),
    )

    /**
     * 生成目标名词的完整六格（单数与复数）变格表
     */
    fun generate(
        lemma: String,
        gender: Gender? = null,
        isAnimate: Boolean? = null,
    ): Pair<Lexeme.CaseForms, Lexeme.CaseForms> {
        val clean = lemma.trim().lowercase().replace("\u0301", "")
        if (clean.isBlank()) return Lexeme.CaseForms() to Lexeme.CaseForms()

        // 1. 优先匹配不规则词表
        IRREGULAR_NOUNS[clean]?.let { return it }

        // 推断有生命性
        val effectiveAnimacy = isAnimate ?: inferAnimacy(clean, gender)

        return when {
            // 2. -ия 阴性特殊名词
            clean.endsWith("ия") -> {
                val stem = clean.removeSuffix("ия")
                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "ии",
                    dative = stem + "ии",
                    accusative = stem + "ию",
                    instrumental = stem + "ией",
                    prepositional = stem + "ии",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "ии",
                    genitive = stem + "ий",
                    dative = stem + "иям",
                    accusative = if (effectiveAnimacy) stem + "ий" else stem + "ии",
                    instrumental = stem + "иями",
                    prepositional = stem + "иях",
                )
                sg to pl
            }

            // 3. -ие 中性特殊名词
            clean.endsWith("ие") -> {
                val stem = clean.removeSuffix("ие")
                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "ия",
                    dative = stem + "ию",
                    accusative = clean,
                    instrumental = stem + "ием",
                    prepositional = stem + "ии",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "ия",
                    genitive = stem + "ий",
                    dative = stem + "иям",
                    accusative = stem + "ия",
                    instrumental = stem + "иями",
                    prepositional = stem + "иях",
                )
                sg to pl
            }

            // 4. -а 结尾名词（包含 папа, дедушка 等阳性，及 книга, мама 等阴性）
            clean.endsWith("а") -> {
                val stem = clean.removeSuffix("а")
                val lastChar = stem.lastOrNull()
                val genSg = if (lastChar in SEVEN_LETTERS) stem + "и" else stem + "ы"
                val instSg = if (lastChar in FIVE_LETTERS) stem + "ей" else stem + "ой"
                val nomPl = genSg
                val genPl = buildZeroEndingPluralGenitive(stem)
                val accPl = if (effectiveAnimacy) genPl else nomPl

                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = genSg,
                    dative = stem + "е",
                    accusative = stem + "у",
                    instrumental = instSg,
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = nomPl,
                    genitive = genPl,
                    dative = stem + "ам",
                    accusative = accPl,
                    instrumental = stem + "ами",
                    prepositional = stem + "ах",
                )
                sg to pl
            }

            // 5. -я 结尾名词（如 неделя, дядя）
            clean.endsWith("я") -> {
                val stem = clean.removeSuffix("я")
                val genPl = if (stem.endsWith("ь")) stem + "ей" else stem + "ь"
                val accPl = if (effectiveAnimacy) genPl else stem + "и"

                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "и",
                    dative = stem + "е",
                    accusative = stem + "ю",
                    instrumental = stem + "ей",
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "и",
                    genitive = genPl,
                    dative = stem + "ям",
                    accusative = accPl,
                    instrumental = stem + "ями",
                    prepositional = stem + "ях",
                )
                sg to pl
            }

            // 6. -о 中性名词（如 окно, дело）
            clean.endsWith("о") -> {
                val stem = clean.removeSuffix("о")
                val genPl = buildNeuterZeroEndingGenitive(stem)
                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "а",
                    dative = stem + "у",
                    accusative = clean,
                    instrumental = stem + "ом",
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "а",
                    genitive = genPl,
                    dative = stem + "ам",
                    accusative = stem + "а",
                    instrumental = stem + "ами",
                    prepositional = stem + "ах",
                )
                sg to pl
            }

            // 7. -е / -ё 中性名词（如 море, поле）
            clean.endsWith("е") || clean.endsWith("ё") -> {
                val stem = clean.dropLast(1)
                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "я",
                    dative = stem + "ю",
                    accusative = clean,
                    instrumental = stem + "ем",
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "я",
                    genitive = stem + "ей",
                    dative = stem + "ям",
                    accusative = stem + "я",
                    instrumental = stem + "ями",
                    prepositional = stem + "ях",
                )
                sg to pl
            }

            // 8. -й 阳性名词（如 музей, чай）
            clean.endsWith("й") -> {
                val stem = clean.removeSuffix("й")
                val accSg = if (effectiveAnimacy) stem + "я" else clean
                val accPl = if (effectiveAnimacy) stem + "ев" else stem + "и"
                val sg = Lexeme.CaseForms(
                    nominative = clean,
                    genitive = stem + "я",
                    dative = stem + "ю",
                    accusative = accSg,
                    instrumental = stem + "ем",
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = stem + "и",
                    genitive = stem + "ев",
                    dative = stem + "ям",
                    accusative = accPl,
                    instrumental = stem + "ями",
                    prepositional = stem + "ях",
                )
                sg to pl
            }

            // 9. -ь 结尾名词
            clean.endsWith("ь") -> {
                val stem = clean.removeSuffix("ь")
                if (gender == Gender.MASCULINE) {
                    // 阳性第二变格（如 словарь, преподаватель）
                    val accSg = if (effectiveAnimacy) stem + "я" else clean
                    val accPl = if (effectiveAnimacy) stem + "ей" else stem + "и"
                    val sg = Lexeme.CaseForms(
                        nominative = clean,
                        genitive = stem + "я",
                        dative = stem + "ю",
                        accusative = accSg,
                        instrumental = stem + "ем",
                        prepositional = stem + "е",
                    )
                    val pl = Lexeme.CaseForms(
                        nominative = stem + "и",
                        genitive = stem + "ей",
                        dative = stem + "ям",
                        accusative = accPl,
                        instrumental = stem + "ями",
                        prepositional = stem + "ях",
                    )
                    sg to pl
                } else {
                    // 阴性第三变格（如 тетрадь, дверь, ночь）
                    val accPl = if (effectiveAnimacy) stem + "ей" else stem + "и"
                    val sg = Lexeme.CaseForms(
                        nominative = clean,
                        genitive = stem + "и",
                        dative = stem + "и",
                        accusative = clean,
                        instrumental = stem + "ью",
                        prepositional = stem + "и",
                    )
                    val pl = Lexeme.CaseForms(
                        nominative = stem + "и",
                        genitive = stem + "ей",
                        dative = stem + "ям",
                        accusative = accPl,
                        instrumental = stem + "ями",
                        prepositional = stem + "ях",
                    )
                    sg to pl
                }
            }

            // 10. 硬辅音结尾阳性（如 стол, студент）
            else -> {
                val stem = clean
                val lastChar = stem.lastOrNull()
                val accSg = if (effectiveAnimacy) stem + "а" else stem
                val nomPl = if (lastChar in SEVEN_LETTERS) stem + "и" else stem + "ы"
                val genPl = if (lastChar in SIBILANTS) stem + "ей" else stem + "ов"
                val accPl = if (effectiveAnimacy) genPl else nomPl
                val instSg = if (lastChar in FIVE_LETTERS) stem + "ем" else stem + "ом"

                val sg = Lexeme.CaseForms(
                    nominative = stem,
                    genitive = stem + "а",
                    dative = stem + "у",
                    accusative = accSg,
                    instrumental = instSg,
                    prepositional = stem + "е",
                )
                val pl = Lexeme.CaseForms(
                    nominative = nomPl,
                    genitive = genPl,
                    dative = stem + "ам",
                    accusative = accPl,
                    instrumental = stem + "ами",
                    prepositional = stem + "ах",
                )
                sg to pl
            }
        }
    }

    private fun buildZeroEndingPluralGenitive(stem: String): String {
        val vowels = "аеёиоуыэюя"
        if (stem.length > 2 && stem.last() == 'к' && stem[stem.length - 2] !in vowels) {
            val base = stem.dropLast(1)
            val inserted = if (base.last() in "гкхжчшщ") "ок" else "ек"
            return base + inserted
        }
        return stem
    }

    private fun buildNeuterZeroEndingGenitive(stem: String): String {
        val vowels = "аеёиоуыэюя"
        if (stem.length > 2 && stem.last() == 'н' && stem[stem.length - 2] !in vowels) {
            return stem.dropLast(1) + "он"
        }
        if (stem.length > 2 && stem.last() == 'к' && stem[stem.length - 2] !in vowels) {
            return stem.dropLast(1) + "ок"
        }
        return stem
    }

    private fun inferAnimacy(clean: String, gender: Gender?): Boolean {
        val knownAnimates = setOf(
            "папа", "дедушка", "мужчина", "юноша", "дядя", "мальчик",
            "студент", "преподаватель", "брат", "друг", "кот", "сын", "отец",
            "повар", "директор", "врач", "человек", "мама", "девушка",
            "женщина", "сестра", "дочь", "мать", "кошка", "собака",
            "подруга", "студентка", "коллега", "староста"
        )
        if (clean in knownAnimates) return true
        if (clean.endsWith("тель") || clean.endsWith("ист") || clean.endsWith("ник") || clean.endsWith("ец") || clean.endsWith("ица")) {
            return true
        }
        return false
    }
}
