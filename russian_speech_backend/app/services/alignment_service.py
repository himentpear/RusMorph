import subprocess
from dataclasses import dataclass
from pathlib import Path

from app.config import Settings
from app.utils.exceptions import AlignmentFailed, DependencyUnavailable
from app.utils.process import executable_environment


@dataclass(frozen=True)
class Interval:
    label: str
    start_ms: int
    end_ms: int


@dataclass(frozen=True)
class AlignmentResult:
    words: list[Interval]
    phones: list[Interval]
    confidence: float
    textgrid_path: Path
    audio_path: Path


class AlignmentService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def align(self, audio_path: Path, target_text: str, work_dir: Path) -> AlignmentResult:
        output = work_dir / "aligned"
        temp = work_dir / "mfa-temp"
        output.mkdir()
        transcript = work_dir / "utterance.lab"
        textgrid = output / "utterance.TextGrid"
        transcript.write_text(target_text, encoding="utf-8")

        command = [
            self.settings.mfa_binary, "align_one",
            str(audio_path),
            str(transcript),
            self.settings.mfa_dictionary,
            self.settings.mfa_acoustic_model,
            str(textgrid),
            "--num_jobs", "1",
            "--clean", "--single_speaker",
            "--quiet",
            "--temporary_directory", str(temp),
        ]
        if self.settings.mfa_g2p_model:
            command.extend(["--g2p_model_path", self.settings.mfa_g2p_model])
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=300,
                check=False,
                env=executable_environment(self.settings.mfa_binary),
            )
        except FileNotFoundError as exc:
            raise DependencyUnavailable("Montreal Forced Aligner", str(exc)) from exc
        except subprocess.TimeoutExpired as exc:
            raise AlignmentFailed("MFA 执行超时") from exc
        if result.returncode == 0 and textgrid.exists():
            return self._parse(textgrid, audio_path)
        raise AlignmentFailed((result.stderr or result.stdout)[-600:])

    @staticmethod
    def _parse(path: Path, audio_path: Path) -> AlignmentResult:
        try:
            from praatio import textgrid
            grid = textgrid.openTextgrid(str(path), includeEmptyIntervals=False)
            tier_names = {name.lower(): name for name in grid.tierNames}
            word_name = tier_names.get("words")
            phone_name = tier_names.get("phones")
            if not word_name or not phone_name:
                raise ValueError("TextGrid 缺少 words/phones 层")
            words = [
                Interval(entry.label, round(entry.start * 1000), round(entry.end * 1000))
                for entry in grid.getTier(word_name).entries
                if entry.label.strip() and entry.label.strip().lower() != "<eps>"
            ]
            phones = [
                Interval(entry.label, round(entry.start * 1000), round(entry.end * 1000))
                for entry in grid.getTier(phone_name).entries if entry.label.strip()
            ]
        except Exception as exc:
            raise AlignmentFailed(f"TextGrid 解析失败：{exc}") from exc
        if not words or not phones:
            raise AlignmentFailed("对齐结果没有词或音素区间")
        # MFA does not expose calibrated utterance posterior in TextGrid.
        coverage = sum(max(0, p.end_ms - p.start_ms) for p in phones)
        span = max(1, words[-1].end_ms - words[0].start_ms)
        confidence = min(0.85, max(0.55, coverage / span * 0.85))
        return AlignmentResult(words, phones, round(confidence, 4), path, audio_path)
