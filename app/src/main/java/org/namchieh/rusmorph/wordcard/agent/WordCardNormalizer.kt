package org.namchieh.rusmorph.wordcard.agent

import com.google.gson.Gson
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.wordcard.model.*

/**
 * 单词卡片数据归一化转换器。
 * 将 Agent DTO 或本地 Room 数据模型映射为稳定严谨的 Lexeme 与 WordForm 领域模型。
 * 严格执行降级保护：缺失字段安全回退，绝不导致抛异常或界面空白。
 */
object WordCardNormalizer {
    private val gson = Gson()

    /**
     * 将经过校验的 WordCardSchemaDto 归一化为 (Lexeme, WordForm)
     */
    fun normalizeFromDto(dto: WordCardSchemaDto, fallbackQuery: String): Pair<Lexeme, WordForm> {
        val lexDto = dto.lexeme
        val lemma = lexDto?.lemma?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackQuery.trim()
        val displayForm = lexDto?.displayForm?.trim().takeUnless { it.isNullOrBlank() } ?: lemma

        val basicDto = lexDto?.basic
        val pos = basicDto?.partOfSpeech?.trim()?.lowercase().orEmpty()
        val gender = parseGender(basicDto?.gender)
        val animacy = basicDto?.animacy

        val basicInfo = Lexeme.BasicInfo(
            partOfSpeech = pos.ifBlank { "word" },
            gender = gender,
            animacy = animacy,
            cefr = basicDto?.cefr?.trim()?.takeIf { it.isNotBlank() },
            translationsZh = basicDto?.translationsZh?.filter { it.isNotBlank() }
                ?: listOfNotNull(basicDto?.shortDefinitionZh?.takeIf { it.isNotBlank() }),
            shortDefinitionZh = basicDto?.shortDefinitionZh?.trim().orEmpty(),
        )

        val pronDto = lexDto?.pronunciation
        val pronInfo = Lexeme.PronunciationInfo(
            stressIndex = pronDto?.stressIndex,
            stressPattern = pronDto?.stressPattern.orEmpty(),
            ipa = pronDto?.ipa?.trim()?.takeIf { it.isNotBlank() },
            syllables = pronDto?.syllables ?: emptyList(),
            pronunciationNote = pronDto?.pronunciationNote.orEmpty(),
        )

        val morphologyInfo = parseMorphology(pos, lexDto?.morphology)

        val usageDto = lexDto?.usage
        val usageInfo = Lexeme.UsageInfo(
            exampleRu = usageDto?.exampleRu.orEmpty(),
            exampleZh = usageDto?.exampleZh.orEmpty(),
            collocations = usageDto?.collocations?.map {
                Lexeme.UsageInfo.Collocation(it.phraseRu.orEmpty(), it.translationZh.orEmpty())
            } ?: emptyList(),
        )

        val learningDto = lexDto?.learning
        val learningInfo = Lexeme.LearningInfo(
            commonErrors = learningDto?.commonErrors ?: emptyList(),
            memoryHint = learningDto?.memoryHint.orEmpty(),
            recommendedFocus = learningDto?.recommendedFocus ?: emptyList(),
        )

        val lexeme = Lexeme(
            id = "lex_${lemma.replace(" ", "_")}",
            lemma = lemma,
            displayForm = displayForm,
            language = lexDto?.language ?: "ru",
            basic = basicInfo,
            pronunciation = pronInfo,
            morphology = morphologyInfo,
            usage = usageInfo,
            learning = learningInfo,
            schemaVersion = dto.schemaVersion,
        )

        // Parse Form
        val formDto = dto.form
        val inputForm = formDto?.inputForm?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackQuery.trim()
        val formDisplay = formDto?.displayForm?.trim().takeUnless { it.isNullOrBlank() } ?: inputForm
        val isLemma = formDto?.isLemma ?: (inputForm.equals(lemma, ignoreCase = true))

        val analysesList = mutableListOf<FormAnalysis>()
        formDto?.analysis?.let { analysesList.add(parseFormAnalysis(it)) }
        formDto?.analyses?.forEach { analysesList.add(parseFormAnalysis(it)) }

        val wordForm = WordForm(
            inputForm = inputForm,
            displayForm = formDisplay,
            isLemma = isLemma,
            analyses = analysesList.distinct(),
            explanationZh = formDto?.explanationZh.orEmpty(),
        )

        return lexeme to wordForm
    }

    /**
     * 将本地数据库现有词条 LexiconEntryWithDetails 映射为完整的 Lexeme 骨架
     */
    fun normalizeFromLocalEntry(entryWithDetails: LexiconEntryWithDetails): Pair<Lexeme, WordForm> {
        val e = entryWithDetails.entry
        val lemma = e.lemma
        val displayForm = e.displayForm.ifBlank { lemma }
        val meanings = e.chineseMeaning?.split("；", ";", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val posList = entryWithDetails.partsOfSpeech.map { it.partOfSpeech }.filter { it.isNotBlank() && it != "8" }
        val primaryPos = posList.firstOrNull() ?: "名词"

        val gender = parseGender(e.gender)
        val basicInfo = Lexeme.BasicInfo(
            partOfSpeech = primaryPos,
            gender = gender,
            animacy = null,
            cefr = null,
            translationsZh = meanings,
            shortDefinitionZh = e.chineseMeaning.orEmpty(),
        )

        val pronInfo = Lexeme.PronunciationInfo(
            stressPattern = e.pluralStressPattern.orEmpty(),
            pronunciationNote = e.phoneticAlternation?.takeIf { it.isNotBlank() }?.let { "语音交替: $it" }.orEmpty(),
        )

        val morphologyInfo = when {
            primaryPos.contains("名") || e.declensionClass != null -> {
                Lexeme.MorphologyInfo.Noun(
                    declensionType = e.declensionClass.orEmpty(),
                    stem = e.endingType.orEmpty(),
                    stressPattern = e.pluralStressPattern.orEmpty(),
                )
            }
            primaryPos.contains("动") || e.conjugationClass != null -> {
                Lexeme.MorphologyInfo.Verb(
                    aspect = if (e.aspect?.contains("完") == true) Aspect.PERFECTIVE else Aspect.IMPERFECTIVE,
                    conjugationType = e.conjugationClass.orEmpty(),
                )
            }
            else -> Lexeme.MorphologyInfo.Generic(
                summaryZh = listOfNotNull(e.gender, e.declensionClass, e.aspect).joinToString(" · ")
            )
        }

        val lexeme = Lexeme(
            id = e.id,
            lemma = lemma,
            displayForm = displayForm,
            basic = basicInfo,
            pronunciation = pronInfo,
            morphology = morphologyInfo,
            usage = Lexeme.UsageInfo(),
            learning = Lexeme.LearningInfo(),
        )

        val wordForm = WordForm(
            inputForm = lemma,
            displayForm = displayForm,
            isLemma = true,
            analyses = listOf(FormAnalysis(noteZh = "词典原形")),
        )

        return lexeme to wordForm
    }

    private fun parseMorphology(pos: String, element: com.google.gson.JsonElement?): Lexeme.MorphologyInfo {
        if (element == null || !element.isJsonObject) return Lexeme.MorphologyInfo.Empty
        return try {
            when {
                pos.contains("noun") || pos.contains("名") -> {
                    val dto = gson.fromJson(element, WordCardSchemaDto.NounMorphologyDto::class.java)
                    Lexeme.MorphologyInfo.Noun(
                        declensionType = dto.declensionType.orEmpty(),
                        stem = dto.stem.orEmpty(),
                        stressPattern = dto.stressPattern.orEmpty(),
                        singular = mapCaseForms(dto.declension?.singular),
                        plural = mapCaseForms(dto.declension?.plural),
                    )
                }
                pos.contains("verb") || pos.contains("动") -> {
                    val dto = gson.fromJson(element, WordCardSchemaDto.VerbMorphologyDto::class.java)
                    Lexeme.MorphologyInfo.Verb(
                        aspect = if (dto.aspect?.contains("perf") == true && !dto.aspect.contains("imp")) Aspect.PERFECTIVE else Aspect.IMPERFECTIVE,
                        aspectPair = dto.aspectPair,
                        conjugationType = dto.conjugationType.orEmpty(),
                        reflexive = dto.reflexive ?: false,
                        present = dto.present?.let {
                            Lexeme.PersonForms(
                                it.firstSingular.orEmpty(), it.secondSingular.orEmpty(), it.thirdSingular.orEmpty(),
                                it.firstPlural.orEmpty(), it.secondPlural.orEmpty(), it.thirdPlural.orEmpty(),
                            )
                        },
                        future = dto.future?.let {
                            Lexeme.PersonForms(
                                it.firstSingular.orEmpty(), it.secondSingular.orEmpty(), it.thirdSingular.orEmpty(),
                                it.firstPlural.orEmpty(), it.secondPlural.orEmpty(), it.thirdPlural.orEmpty(),
                            )
                        },
                        past = dto.past?.let {
                            Lexeme.PastForms(it.masculine.orEmpty(), it.feminine.orEmpty(), it.neuter.orEmpty(), it.plural.orEmpty())
                        },
                        imperative = dto.imperative?.let {
                            Lexeme.ImperativeForms(it.singular.orEmpty(), it.plural.orEmpty())
                        },
                    )
                }
                pos.contains("adj") || pos.contains("形") -> {
                    val dto = gson.fromJson(element, WordCardSchemaDto.AdjectiveMorphologyDto::class.java)
                    Lexeme.MorphologyInfo.Adjective(
                        adjectiveType = dto.adjectiveType.orEmpty(),
                        shortForms = dto.shortForms?.let {
                            Lexeme.ShortForms(it.masculine.orEmpty(), it.feminine.orEmpty(), it.neuter.orEmpty(), it.plural.orEmpty())
                        },
                        agreementNominative = dto.agreement?.nominative?.let {
                            Lexeme.GenderForms(it.masculine.orEmpty(), it.feminine.orEmpty(), it.neuter.orEmpty(), it.plural.orEmpty())
                        } ?: Lexeme.GenderForms(),
                        comparative = dto.comparison?.comparative,
                        superlative = dto.comparison?.superlative,
                    )
                }
                else -> Lexeme.MorphologyInfo.Generic(summaryZh = element.toString())
            }
        } catch (_: Exception) {
            Lexeme.MorphologyInfo.Generic(summaryZh = "形态格式解析降级")
        }
    }

    private fun mapCaseForms(dto: WordCardSchemaDto.CaseFormsDto?): Lexeme.CaseForms {
        if (dto == null) return Lexeme.CaseForms()
        return Lexeme.CaseForms(
            nominative = dto.nominative.orEmpty(),
            genitive = dto.genitive.orEmpty(),
            dative = dto.dative.orEmpty(),
            accusative = dto.accusative.orEmpty(),
            instrumental = dto.instrumental.orEmpty(),
            prepositional = dto.prepositional.orEmpty(),
        )
    }

    private fun parseFormAnalysis(dto: WordCardSchemaDto.FormAnalysisDto): FormAnalysis {
        return FormAnalysis(
            grammaticalCase = parseCase(dto.case),
            number = parseNumber(dto.number),
            gender = parseGender(dto.gender),
            person = parsePerson(dto.person),
            tense = parseTense(dto.tense),
            mood = parseMood(dto.mood),
            aspect = parseAspect(dto.aspect),
            noteZh = dto.noteZh,
        )
    }

    private fun parseCase(v: String?): GrammaticalCase? = when (v?.trim()?.lowercase()) {
        "nominative", "nom", "именительный", "主格" -> GrammaticalCase.NOMINATIVE
        "genitive", "gen", "родительный", "生格" -> GrammaticalCase.GENITIVE
        "dative", "dat", "дательный", "与格" -> GrammaticalCase.DATIVE
        "accusative", "acc", "винительный", "宾格" -> GrammaticalCase.ACCUSATIVE
        "instrumental", "ins", "inst", "творительный", "工具格" -> GrammaticalCase.INSTRUMENTAL
        "prepositional", "prep", "locative", "предложный", "前置格" -> GrammaticalCase.PREPOSITIONAL
        else -> null
    }

    private fun parseNumber(v: String?): GrammaticalNumber? = when (v?.trim()?.lowercase()) {
        "singular", "sg", "единственное", "单数" -> GrammaticalNumber.SINGULAR
        "plural", "pl", "множественное", "复数" -> GrammaticalNumber.PLURAL
        else -> null
    }

    private fun parseGender(v: String?): Gender? = when (v?.trim()?.lowercase()) {
        "masculine", "m", "мужской", "阳", "阳性" -> Gender.MASCULINE
        "feminine", "f", "женский", "阴", "阴性" -> Gender.FEMININE
        "neuter", "n", "средний", "中", "中性" -> Gender.NEUTER
        else -> null
    }

    private fun parsePerson(v: String?): GrammaticalPerson? = when (v?.trim()?.lowercase()) {
        "1", "1sg", "1pl", "first", "第一人称" -> GrammaticalPerson.FIRST
        "2", "2sg", "2pl", "second", "第二人称" -> GrammaticalPerson.SECOND
        "3", "3sg", "3pl", "third", "第三人称" -> GrammaticalPerson.THIRD
        else -> null
    }

    private fun parseTense(v: String?): Tense? = when (v?.trim()?.lowercase()) {
        "present", "现在时" -> Tense.PRESENT
        "past", "过去时" -> Tense.PAST
        "future", "将来时" -> Tense.FUTURE
        else -> null
    }

    private fun parseMood(v: String?): Mood? = when (v?.trim()?.lowercase()) {
        "indicative", "陈述", "陈述语气" -> Mood.INDICATIVE
        "imperative", "祈使", "祈使语气" -> Mood.IMPERATIVE
        "conditional", "假定", "假定语气" -> Mood.CONDITIONAL
        else -> null
    }

    private fun parseAspect(v: String?): Aspect? = when (v?.trim()?.lowercase()) {
        "imperfective", "impf", "未完成体" -> Aspect.IMPERFECTIVE
        "perfective", "pf", "完成体" -> Aspect.PERFECTIVE
        else -> null
    }
}
