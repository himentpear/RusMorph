import importlib.util
import shutil

from fastapi import APIRouter, Depends

from app.api.dependencies import services
from app.services.container import ServiceContainer

router = APIRouter(tags=["health"])


@router.get("/health")
def health(container: ServiceContainer = Depends(services)):
    dependencies = {
        "ffmpeg": shutil.which(container.settings.ffmpeg_binary) is not None,
        "mfa": shutil.which(container.settings.mfa_binary) is not None,
        "faster_whisper": importlib.util.find_spec("faster_whisper") is not None,
        "parselmouth": importlib.util.find_spec("parselmouth") is not None,
        "ruaccent": importlib.util.find_spec("ruaccent") is not None,
    }
    return {
        "status": "ok" if dependencies["ffmpeg"] else "degraded",
        "dependencies": dependencies,
        "asr_backend": container.settings.asr_backend,
        "asr_model": container.settings.asr_model,
        "device": container.settings.asr_device,
        "scoring": (
            "asr_timestamp_acoustic_proxy"
            if container.settings.pronunciation_alignment_backend == "asr_timestamps"
            else "mfa_alignment_acoustic_proxy"
        ),
        "gop_available": False,
    }
