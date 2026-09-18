package org.namchieh.rusmorph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.data.remote.PronunciationDto
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.repository.PronunciationExampleResult
import org.namchieh.rusmorph.data.repository.SpeechRepository
import org.namchieh.rusmorph.data.repository.LearningRepository
import org.namchieh.rusmorph.domain.learning.PronunciationSession
import org.namchieh.rusmorph.domain.learning.PronunciationSessionType
import java.util.UUID

data class PronunciationUiState(
    val targetText: String = "Я изучаю русский язык.",
    val difficulty: String = "beginner",
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val result: PronunciationDto? = null,
    val error: String? = null,
    val userRecordingPath: String? = null,
    val isGeneratingExample: Boolean = false,
    val exampleChinese: String? = null,
)

class PronunciationViewModel(
    private val repository: SpeechRepository,
    private val agentRepository: AgentRepository,
    private val learningRepository: LearningRepository? = null,
    private val sessionType: PronunciationSessionType = PronunciationSessionType.FREE,
    private val sourceId: String? = null,
    private val lessonId: String? = null,
    private val wordBookId: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PronunciationUiState())
    val uiState: StateFlow<PronunciationUiState> = _uiState

    fun setTargetText(value: String) {
        _uiState.value = _uiState.value.copy(
            targetText = value,
            result = null,
            error = null,
            exampleChinese = null,
        )
    }

    fun setDifficulty(value: String) {
        _uiState.value = _uiState.value.copy(difficulty = value)
    }

    fun recordingStarted() {
        _uiState.value = _uiState.value.copy(isRecording = true, error = null)
    }

    fun recordingCancelled() {
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    fun generateExample() {
        val snapshot = _uiState.value
        if (snapshot.isGeneratingExample || snapshot.isAnalyzing || snapshot.isRecording) return
        _uiState.value = snapshot.copy(isGeneratingExample = true, error = null)
        viewModelScope.launch {
            val topic = snapshot.targetText
                .trim()
                .takeIf { it.isNotBlank() && it.length <= 80 }
            when (
                val generated = agentRepository.generatePronunciationExample(
                    snapshot.difficulty,
                    topic,
                )
            ) {
                is PronunciationExampleResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        targetText = generated.example.russian,
                        exampleChinese = generated.example.chinese,
                        isGeneratingExample = false,
                        result = null,
                        error = null,
                    )
                }
                is PronunciationExampleResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingExample = false,
                        error = when (generated.error) {
                            org.namchieh.rusmorph.agent.AgentError.ServiceNotConfigured ->
                                "AI 例句服务尚未配置"
                            org.namchieh.rusmorph.agent.AgentError.Timeout ->
                                "AI 例句生成超时，请重试"
                            org.namchieh.rusmorph.agent.AgentError.NoNetwork ->
                                "无法连接 AI 例句服务"
                            else -> "AI 例句生成失败，请稍后重试"
                        },
                    )
                }
            }
        }
    }

    fun analyze(file: File) {
        val snapshot = _uiState.value
        val startedAt = System.currentTimeMillis()
        if (snapshot.targetText.isBlank()) {
            file.delete()
            _uiState.value = snapshot.copy(isRecording = false, error = "请先输入俄语目标文本")
            return
        }
        snapshot.userRecordingPath
            ?.takeIf { it != file.absolutePath }
            ?.let(::File)
            ?.delete()
        _uiState.value = snapshot.copy(
            isRecording = false,
            isAnalyzing = true,
            error = null,
            userRecordingPath = file.absolutePath,
        )
        viewModelScope.launch {
            try {
                val result = repository.analyze(file, snapshot.targetText, snapshot.difficulty)
                _uiState.value = _uiState.value.copy(isAnalyzing = false, result = result)
                learningRepository?.recordPronunciation(
                    PronunciationSession(
                        id = UUID.randomUUID().toString(), type = sessionType, sourceId = sourceId,
                        wordBookId = wordBookId, lessonId = lessonId, targetText = snapshot.targetText,
                        startedAt = startedAt, completedAt = System.currentTimeMillis(),
                        intelligibilityScore = result.overall_score,
                    ),
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = exception.message ?: "朗读分析失败",
                )
            }
        }
    }

    override fun onCleared() {
        _uiState.value.userRecordingPath?.let(::File)?.delete()
        super.onCleared()
    }
}
