import subprocess
from pathlib import Path

from app.config import Settings
from app.utils.exceptions import DependencyUnavailable, SpeechError
from app.utils.process import executable_environment


class G2pService:
    def __init__(self, settings: Settings):
        self.settings = settings

    def generate_dictionary(self, word_list: Path, output_dictionary: Path) -> None:
        command = [
            self.settings.mfa_binary, "g2p",
            self.settings.mfa_g2p_model,
            str(word_list),
            str(output_dictionary),
            "--clean",
        ]
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=180,
                check=False,
                env=executable_environment(self.settings.mfa_binary),
            )
        except FileNotFoundError as exc:
            raise DependencyUnavailable("Montreal Forced Aligner", str(exc)) from exc
        if result.returncode != 0:
            raise SpeechError("g2p_failed", "MFA G2P 无法为目标文本生成发音词典")
