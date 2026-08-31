from difflib import SequenceMatcher

import numpy as np

from app.config import Settings
from app.domain.russian_syllabifier import russian_words, strip_stress
from app.domain.score_rules import clamp, pronunciation_accuracy, proficiency, status_for
from app.schemas.asr import WordTimestamp
from app.schemas.pronunciation import PronunciationResponse, TextSubstitution, WordAnalysis
from app.services.acoustic_service import AcousticService
from app.services.alignment_service import AlignmentResult, AlignmentService
from app.services.asr_service import AsrEngine
from app.services.audio_preprocessor import AudioInfo
from app.services.feedback_service import FeedbackService
from app.services.pronunciation_score_service import PronunciationScoreService
from app.services.stress_service import StressService
from app.utils.files import temporary_directory


class PronunciationPipeline:
    def __init__(
        self,
        settings: Settings,
        asr: AsrEngine,
        stress: StressService,
        alignment: AlignmentService,
        acoustic: AcousticService,
        scoring: PronunciationScoreService,
        feedback: FeedbackService,
    ):
        self.settings = settings
        self.asr = asr
        self.stress = stress
        self.alignment = alignment
        self.acoustic = acoustic
        self.scoring = scoring
        self.feedback = feedback

    def analyze(
        self, audio: AudioInfo, target_text: str, difficulty: str
    ) -> PronunciationResponse:
        fast_alignment = self.settings.pronunciation_alignment_backend == "asr_timestamps"
        if fast_alignment:
            stressed_text, stress_warnings = target_text, []
        else:
            stressed_text, stress_warnings = self.stress.accent_text(target_text)
        target_words = russian_words(target_text)
        asr = self.asr.transcribe(audio.path, prompt=target_text)
        recognized_words = russian_words(asr.text)
        comparison = self.scoring.compare_text(target_words, recognized_words)
        text_match = comparison.score
        text_match_confidence = round(asr.confidence * min(1.0, text_match / 80), 4)
        warnings = list(audio.warnings) + stress_warnings

        if text_match < 45 or asr.confidence < 0.45:
            return self._unavailable(
                target_text,
                stressed_text,
                asr.text,
                text_match,
                asr.confidence,
                text_match_confidence,
                audio,
                comparison,
                warnings,
                "识别文本与目标文本差异过大，无法可靠评分",
            )

        if fast_alignment:
            word_analyses, alignment_confidence = self._analyze_words_from_asr(
                audio.path,
                target_words,
                asr.words,
                playback_offset_ms=audio.trim_start_ms,
            )
        else:
            with temporary_directory(self.settings, "analysis-") as work:
                aligned = self.alignment.align(audio.path, target_text, work)
                word_analyses = self._analyze_words_mfa(
                    aligned,
                    target_words,
                    playback_offset_ms=audio.trim_start_ms,
                )
                alignment_confidence = aligned.confidence

        scored_words = [word for word in word_analyses if word.score is not None]
        if not scored_words:
            return self._unavailable(
                target_text,
                stressed_text,
                asr.text,
                text_match,
                asr.confidence,
                text_match_confidence,
                audio,
                comparison,
                warnings,
                "单词声学证据不足，无法可靠评分",
                alignment_confidence=alignment_confidence,
            )

        pronunciation = clamp(float(np.mean([word.score for word in scored_words])))
        fluency, words_per_minute = self.scoring.fluency(
            audio.speech_duration_ms,
            [(word.start_ms, word.end_ms) for word in word_analyses],
            len(target_words),
            difficulty,
        )
        scored_confidences = [word.confidence for word in scored_words]
        word_score_confidence = (
            alignment_confidence
            if fast_alignment
            else (float(np.mean(scored_confidences)) if scored_confidences else 0.0)
        )
        analysis_confidence = min(
            asr.confidence,
            text_match_confidence,
            alignment_confidence,
            word_score_confidence,
        )
        available = analysis_confidence >= self.settings.minimum_analysis_confidence

        # Word-only mode deliberately omits a separately exposed stress score. The
        # existing overall weighting treats pronunciation as the stress proxy so
        # historical total scores remain comparable.
        overall = (
            self.scoring.overall(pronunciation, pronunciation, fluency, comparison.score)
            if available
            else None
        )
        low_words = [
            word.text
            for word in word_analyses
            if word.score is not None and word.score < 60 and word.confidence >= 0.55
        ]
        issues, summary, practice = self.feedback.build(comparison.omitted, [], low_words)
        if not available:
            summary = "音频或分析证据不足，无法可靠给出总分；请在安静环境靠近麦克风重新录音。"

        return PronunciationResponse(
            success=True,
            target_text=target_text,
            target_stressed_text=stressed_text,
            recognized_text=asr.text,
            text_match_score=text_match,
            score_available=available,
            reason=None if available else "分析置信度不足，无法可靠评分",
            overall_score=overall,
            pronunciation_accuracy=pronunciation if available else None,
            stress_accuracy=None,
            fluency_score=fluency if available else None,
            completeness_score=comparison.score if available else None,
            proficiency_level=proficiency(overall) if overall is not None else None,
            asr_confidence=asr.confidence,
            text_match_confidence=text_match_confidence,
            alignment_confidence=alignment_confidence,
            stress_confidence=0,
            phoneme_score_confidence=round(word_score_confidence, 4),
            analysis_confidence=round(analysis_confidence, 4),
            duration_ms=audio.duration_ms,
            speech_rate_words_per_minute=words_per_minute,
            words=word_analyses,
            omitted_words=comparison.omitted,
            extra_words=comparison.extra,
            substituted_words=[
                TextSubstitution(target=target, recognized=recognized)
                for target, recognized in comparison.substitutions
            ],
            top_issues=issues,
            summary_feedback_zh=summary,
            next_practice=practice,
            warnings=warnings,
        )

    def _analyze_words_mfa(
        self,
        aligned: AlignmentResult,
        target_words: list[str],
        playback_offset_ms: int = 0,
    ) -> list[WordAnalysis]:
        intervals = aligned.words[:len(target_words)]
        features = self.acoustic.analyze_intervals(
            aligned.audio_path,
            [(word.start_ms, word.end_ms) for word in intervals],
        )
        confidence = round(min(0.82, aligned.confidence * 0.90), 4)
        result: list[WordAnalysis] = []
        for text, interval, feature in zip(target_words, intervals, features):
            phoneme, vowel, consonant, _ = self.scoring.acoustic_proxy_score(
                feature, features, aligned.confidence
            )
            score = pronunciation_accuracy(phoneme, vowel, consonant)
            status = status_for(score, confidence)
            feedback = self._word_feedback(score, confidence)
            result.append(WordAnalysis(
                text=text,
                start_ms=interval.start_ms + playback_offset_ms,
                end_ms=interval.end_ms + playback_offset_ms,
                score=score,
                confidence=confidence,
                status=status,
                feedback_zh=feedback,
            ))
        return result

    def _analyze_words_from_asr(
        self,
        audio_path,
        target_words: list[str],
        timestamps: list[WordTimestamp],
        playback_offset_ms: int = 0,
    ) -> tuple[list[WordAnalysis], float]:
        timed_words: list[tuple[str, WordTimestamp]] = []
        for timestamp in timestamps:
            tokens = russian_words(timestamp.text)
            for token in tokens:
                timed_words.append((strip_stress(token).lower(), timestamp))

        normalized_targets = [strip_stress(word).lower() for word in target_words]
        normalized_actual = [word for word, _ in timed_words]
        matcher = SequenceMatcher(
            a=normalized_targets,
            b=normalized_actual,
            autojunk=False,
        )
        mapping: dict[int, tuple[WordTimestamp, float]] = {}
        for tag, target_start, target_end, actual_start, actual_end in matcher.get_opcodes():
            if tag == "equal":
                for offset in range(target_end - target_start):
                    mapping[target_start + offset] = (
                        timed_words[actual_start + offset][1],
                        1.0,
                    )
            elif tag == "replace":
                paired = min(target_end - target_start, actual_end - actual_start)
                for offset in range(paired):
                    mapping[target_start + offset] = (
                        timed_words[actual_start + offset][1],
                        0.68,
                    )

        mapped_indexes = sorted(mapping)
        intervals = [
            (mapping[index][0].start_ms, mapping[index][0].end_ms)
            for index in mapped_indexes
        ]
        features = self.acoustic.analyze_intervals(audio_path, intervals) if intervals else []
        feature_by_index = dict(zip(mapped_indexes, features))
        feature_pool = list(feature_by_index.values())
        result: list[WordAnalysis] = []
        alignment_evidence: list[float] = []

        for index, text in enumerate(target_words):
            mapped = mapping.get(index)
            feature = feature_by_index.get(index)
            if mapped is None or feature is None:
                result.append(WordAnalysis(
                    text=text,
                    start_ms=0,
                    end_ms=0,
                    score=None,
                    confidence=0,
                    status="unreliable",
                    feedback_zh="该单词未被可靠识别，请单独放慢朗读。",
                ))
                continue

            timestamp, match_factor = mapped
            evidence = max(0.0, min(1.0, timestamp.confidence * match_factor))
            alignment_evidence.append(evidence)
            proxy_confidence = max(0.45, evidence)
            phoneme, vowel, consonant, _ = self.scoring.acoustic_proxy_score(
                feature,
                feature_pool,
                proxy_confidence,
            )
            score = pronunciation_accuracy(phoneme, vowel, consonant)
            confidence = round(min(0.82, evidence), 4)
            result.append(WordAnalysis(
                text=text,
                start_ms=timestamp.start_ms + playback_offset_ms,
                end_ms=timestamp.end_ms + playback_offset_ms,
                score=score,
                confidence=confidence,
                status=status_for(score, confidence),
                feedback_zh=self._word_feedback(score, confidence),
            ))

        coverage = len(mapping) / max(1, len(target_words))
        mean_evidence = float(np.mean(alignment_evidence)) if alignment_evidence else 0.0
        alignment_confidence = round(mean_evidence * coverage, 4)
        return result, alignment_confidence

    @staticmethod
    def _word_feedback(score: float, confidence: float) -> str:
        if confidence < 0.55:
            return "当前单词的声学证据不足，建议重新录音。"
        if score >= 90:
            return "发音清晰稳定，继续保持。"
        if score >= 75:
            return "整体发音良好，可注意连读和节奏。"
        if score >= 60:
            return "基本可懂，建议放慢速度单独练习该词。"
        return "需要加强，点击回放并对照目标发音重复练习。"

    def _unavailable(
        self,
        target_text,
        stressed_text,
        recognized_text,
        text_match,
        asr_confidence,
        text_match_confidence,
        audio,
        comparison,
        warnings,
        reason,
        alignment_confidence=0.0,
    ) -> PronunciationResponse:
        issues, _, practice = self.feedback.build(comparison.omitted, [], [])
        return PronunciationResponse(
            success=True,
            target_text=target_text,
            target_stressed_text=stressed_text,
            recognized_text=recognized_text,
            text_match_score=text_match,
            score_available=False,
            reason=reason,
            asr_confidence=asr_confidence,
            text_match_confidence=text_match_confidence,
            alignment_confidence=alignment_confidence,
            stress_confidence=0,
            phoneme_score_confidence=0,
            analysis_confidence=0,
            duration_ms=audio.duration_ms,
            speech_rate_words_per_minute=0,
            words=[],
            omitted_words=comparison.omitted,
            extra_words=comparison.extra,
            substituted_words=[
                TextSubstitution(target=target, recognized=recognized)
                for target, recognized in comparison.substitutions
            ],
            top_issues=issues,
            summary_feedback_zh=reason,
            next_practice=practice,
            warnings=warnings,
        )
