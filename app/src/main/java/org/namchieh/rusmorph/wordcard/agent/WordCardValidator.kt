package org.namchieh.rusmorph.wordcard.agent

import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * WordCard JSON Schema 校验器。
 * 遵循原则：所有 Agent 输出必须先经过 JSON Schema 校验；缺失字段允许降级，不允许崩溃。
 */
object WordCardValidator {
    private val gson = Gson()

    sealed interface ValidationResult {
        data class Success(val dto: WordCardSchemaDto, val warnings: List<String> = emptyList()) : ValidationResult
        data class Failure(val reason: String, val rawContent: String) : ValidationResult
    }

    fun validateAndParse(rawJson: String): ValidationResult {
        if (rawJson.isBlank()) {
            return ValidationResult.Failure("Empty JSON response", rawJson)
        }

        val cleanedJson = cleanJsonFence(rawJson)

        return try {
            val jsonElement = JsonParser.parseString(cleanedJson)
            if (!jsonElement.isJsonObject) {
                return ValidationResult.Failure("Root element is not a JSON object", rawJson)
            }

            val rootObj = jsonElement.asJsonObject
            val schemaVersion = rootObj.get("schema_version")?.asString ?: "1.0"
            val dto = gson.fromJson(rootObj, WordCardSchemaDto::class.java)

            val warnings = mutableListOf<String>()
            if (dto.lexeme?.lemma.isNullOrBlank()) {
                warnings.add("Missing or empty lexeme.lemma")
            }
            if (dto.lexeme?.basic?.partOfSpeech.isNullOrBlank()) {
                warnings.add("Missing basic.part_of_speech")
            }
            val confidence = dto.agentMeta?.confidence ?: 1.0
            if (confidence < 0.75) {
                warnings.add("Low model confidence: $confidence")
            }

            ValidationResult.Success(dto, warnings)
        } catch (e: Exception) {
            ValidationResult.Failure("JSON parsing failed: ${e.message}", rawJson)
        }
    }

    private fun cleanJsonFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```json")) {
            s = s.removePrefix("```json").trim()
        } else if (s.startsWith("```")) {
            s = s.removePrefix("```").trim()
        }
        if (s.endsWith("```")) {
            s = s.removeSuffix("```").trim()
        }
        // If there is preamble text before the first '{' and after the last '}'
        val firstBrace = s.indexOf('{')
        val lastBrace = s.lastIndexOf('}')
        if (firstBrace in 0..<lastBrace) {
            s = s.substring(firstBrace, lastBrace + 1)
        }
        return s
    }
}
