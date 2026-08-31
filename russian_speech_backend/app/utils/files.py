import os
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from fastapi import UploadFile

from app.config import Settings
from app.utils.exceptions import SpeechError


async def save_upload(upload: UploadFile, settings: Settings) -> Path:
    suffix = Path(upload.filename or "audio.bin").suffix.lower()
    if suffix not in settings.allowed_extensions:
        raise SpeechError("unsupported_audio_format", f"不支持的音频格式：{suffix or '未知'}")
    fd, raw_path = tempfile.mkstemp(prefix="upload-", suffix=suffix, dir=settings.temp_dir)
    path = Path(raw_path)
    total = 0
    try:
        with os.fdopen(fd, "wb") as target:
            while chunk := await upload.read(1024 * 1024):
                total += len(chunk)
                if total > settings.max_upload_bytes:
                    raise SpeechError("audio_too_large", "音频文件超过大小限制", 413)
                target.write(chunk)
        if total == 0:
            raise SpeechError("empty_audio", "上传的音频为空")
        return path
    except Exception:
        path.unlink(missing_ok=True)
        raise


@contextmanager
def temporary_directory(settings: Settings, prefix: str) -> Iterator[Path]:
    with tempfile.TemporaryDirectory(prefix=prefix, dir=settings.temp_dir) as value:
        yield Path(value)
