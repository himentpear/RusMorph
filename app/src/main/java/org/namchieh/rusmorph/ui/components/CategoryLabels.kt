package org.namchieh.rusmorph.ui.components

import androidx.annotation.StringRes
import org.namchieh.rusmorph.R

@StringRes
fun knowledgeCategoryLabel(category: String): Int = when (category) {
    "ATHEMATIC_CONJUGATION" -> R.string.category_athematic_conjugation
    "FIRST_PALATALIZATION" -> R.string.category_first_palatalization
    "IOTATION" -> R.string.category_iotation
    "MIXED_CONJUGATION" -> R.string.category_mixed_conjugation
    "VA_SUFFIX_LOSS" -> R.string.category_va_suffix_loss
    "NASAL_VOWEL_REMAINS" -> R.string.category_nasal_vowel_remains
    "STEM_ALTERNATION" -> R.string.category_stem_alternation
    "PAST_TENSE_HISTORY" -> R.string.category_past_tense_history
    "FUTURE_TENSE_HISTORY" -> R.string.category_future_tense_history
    "GENERAL_PROJECT_BACKGROUND" -> R.string.category_general_background
    "PLURAL_ENDING_STRESS" -> R.string.category_plural_ending_stress
    "MOBILE_STRESS" -> R.string.category_mobile_stress
    "STEM_EXPANSION" -> R.string.category_stem_expansion
    "COLLECTIVE_J_SUFFIX" -> R.string.category_collective_j_suffix
    "SEMANTIC_STRESS_DIFFERENTIATION" -> R.string.category_semantic_stress
    else -> R.string.category_other
}

fun String?.visibleValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && it != "8" }

fun readableSummary(content: String, maximumLength: Int = 180): String {
    val compact = content.trim().replace(Regex("\\s+"), " ")
    if (compact.length <= maximumLength) return compact
    val breakAt = compact.lastIndexOf(' ', maximumLength).takeIf { it > maximumLength / 2 }
        ?: maximumLength
    return compact.substring(0, breakAt).trimEnd() + "…"
}
