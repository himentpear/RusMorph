package org.namchieh.rusmorph.data.repository

object RussianMorphologyFuzzy {
    private val NOUN_NEUTER_IE_SUFFIXES = listOf("иями", "иям", "иях", "ием", "ию", "ии", "ия", "ие")
    
    private val ADJ_ENDINGS = listOf(
        "ейшего", "айшего", "ейшему", "айшему", "ейшим", "айшим", "ейших", "айших",
        "енный", "янный", "инный",
        "ыми", "ими", "ого", "его", "ому", "ему", "ых", "их", "ым", "им",
        "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю", "ый", "ий", "ой"
    )
    
    private val VERB_ENDINGS = listOf(
        "ившись", "вшись", "увшись",
        "етесь", "ётесь", "итесь", "уться", "ются", "аться", "яться", "иться",
        "вшись", "вший", "вшая", "вшее", "вшие",
        "ишь", "ешь", "ёшь", "ете", "ёте", "ите", "ут", "ют", "ат", "ят",
        "ет", "ёт", "ит", "ем", "ём", "им",
        "лся", "лась", "лось", "лись", "ла", "ло", "ли", "ть", "ти", "чь"
    )

    private val NOUN_GENERAL_ENDINGS = listOf(
        "ами", "ями", "ах", "ях", "ей", "ов", "ев", "ом", "ем", "ой", "ей", "ам", "ям",
        "а", "я", "у", "ю", "о", "е", "ы", "и"
    )

    /**
     * Generates plausible dictionary/ending candidates based on Russian inflection rules.
     * E.g.: "произдения" -> ["произдение", "произведения"]
     */
    fun generateEndingCandidates(normalized: String): List<String> {
        val results = mutableListOf<String>()
        val len = normalized.length
        if (len < 3) return results

        // 1. Neuter noun -ия <-> -ие (e.g. произведения/произдения -> произведение)
        if (normalized.endsWith("ия")) {
            results.add(normalized.dropLast(2) + "ие")
        } else if (normalized.endsWith("ие")) {
            results.add(normalized.dropLast(2) + "ия")
        } else if (normalized.endsWith("ием") || normalized.endsWith("ию") || normalized.endsWith("ии")) {
            val base = normalized.substring(0, normalized.length - 2)
            results.add(base + "е")
            results.add(base + "я")
        }

        // 2. Adjectives -> dictionary masculine form (-ый / -ий / -ой)
        for (suffix in ADJ_ENDINGS) {
            if (normalized.endsWith(suffix) && len - suffix.length >= 2) {
                val stem = normalized.dropLast(suffix.length)
                results.add(stem + "ый")
                results.add(stem + "ий")
                results.add(stem + "ой")
                results.add(stem)
                break
            }
        }

        // 3. Verbs -> dictionary infinitive form (-ть / -ти)
        for (suffix in VERB_ENDINGS) {
            if (normalized.endsWith(suffix) && len - suffix.length >= 2) {
                val stem = normalized.dropLast(suffix.length)
                results.add(stem + "ть")
                results.add(stem + "ти")
                results.add(stem)
                break
            }
        }

        // 4. Nouns general endings
        for (suffix in NOUN_GENERAL_ENDINGS) {
            if (normalized.endsWith(suffix) && len - suffix.length >= 3) {
                val stem = normalized.dropLast(suffix.length)
                results.add(stem)
                results.add(stem + "а")
                results.add(stem + "о")
                results.add(stem + "ь")
                break
            }
        }

        return results.distinct().filter { it != normalized && it.isNotBlank() }
    }

    /**
     * Russian stem extractor.
     */
    fun extractStem(normalized: String): String {
        for (suffix in (ADJ_ENDINGS + VERB_ENDINGS + NOUN_NEUTER_IE_SUFFIXES).sortedByDescending { it.length }) {
            if (normalized.endsWith(suffix) && normalized.length - suffix.length >= 3) {
                return normalized.dropLast(suffix.length)
            }
        }
        return normalized
    }

    /**
     * Bounded Levenshtein edit distance with early pruning.
     */
    fun editDistanceAtMost(left: String, right: String, maximum: Int): Int {
        if (kotlin.math.abs(left.length - right.length) > maximum) return maximum + 1
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            var rowMinimum = current[0]
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
                rowMinimum = minOf(rowMinimum, current[j + 1])
            }
            if (rowMinimum > maximum) return maximum + 1
            previous = current
        }
        return previous[right.length]
    }
}
