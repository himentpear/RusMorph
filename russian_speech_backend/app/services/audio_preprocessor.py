import json
import subprocess
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import soundfile as sf

from app.config import Settings
from app.utils.exceptions import DependencyUnavailable, SpeechError


@dataclass(frozen=True)
class AudioInfo:
    path: Path
    duration_ms: int
    speech_duration_ms: int
    rms_db: float
    warnings: list[str]
    trim_start_ms: int = 0


class AudioPreprocessor:
    def __init__(self, settings: Settings):
        self.settings = settings

    def normalize(self, source: Path, output: Path) -> AudioInfo:
        command = [
            self.settings.ffmpeg_binary, "-nostdin", "-v", "error", "-y",
            "-i", str(source), "-ac", "1", "-ar", "16000",
            "-c:a", "pcm_s16le", str(output),
        ]
        try:
            completed = subprocess.run(command, capture_output=True, text=True, timeout=90, check=False)
        except FileNotFoundError as exc:
            raise DependencyUnavailable("FFmpeg", str(exc)) from exc
        except subprocess.TimeoutExpired as exc:
            raise SpeechError("audio_processing_timeout", "音频标准化超时") from exc
        if completed.returncode != 0:
            raise SpeechError("corrupt_audio", "音频损坏或格式无法解析")

        samples, sample_rate = sf.read(output, dtype="float32", always_2d=False)
        if samples.size == 0:
            raise SpeechError("empty_audio", "音频中没有可分析的数据")
        duration = samples.size / sample_rate
        if duration > self.settings.max_duration_seconds:
            raise SpeechError("audio_too_long", "录音超过最大时长限制")
        rms = float(np.sqrt(np.mean(np.square(samples))) + 1e-12)
        rms_db = 20 * np.log10(rms)
        threshold = max(10 ** (-45 / 20), rms * 0.18)
        active = np.flatnonzero(np.abs(samples) >= threshold)
        if active.size == 0:
            raise SpeechError("no_speech", "没有检测到有效语音")
        pad = int(sample_rate * 0.08)
        start = max(0, int(active[0]) - pad)
        end = min(samples.size, int(active[-1]) + pad)
        trimmed = samples[start:end]
        sf.write(output, trimmed, sample_rate, subtype="PCM_16")
        speech_duration = trimmed.size / sample_rate
        if speech_duration < self.settings.min_speech_seconds:
            raise SpeechError("recording_too_short", "有效录音不足 300 毫秒")
        warnings: list[str] = []
        if rms_db < -38:
            warnings.append("录音音量过低")
        clipping = float(np.mean(np.abs(samples) > 0.995))
        if clipping > 0.01:
            warnings.append("录音存在明显削波")
        return AudioInfo(
            path=output,
            duration_ms=round(duration * 1000),
            speech_duration_ms=round(speech_duration * 1000),
            rms_db=round(rms_db, 2),
            warnings=warnings,
            trim_start_ms=round(start / sample_rate * 1000),
        )
