import asyncio

from fastapi import APIRouter, Depends, File, Form, UploadFile

from app.api.dependencies import require_internal_gateway, services
from app.schemas.pronunciation import PronunciationResponse
from app.services.container import ServiceContainer
from app.utils.exceptions import SpeechError
from app.utils.files import save_upload, temporary_directory

router = APIRouter(prefix="/api/pronunciation", tags=["pronunciation"])


@router.post("/analyze", response_model=PronunciationResponse)
async def analyze(
    audio: UploadFile = File(...),
    target_text: str = Form(..., min_length=1, max_length=500),
    exercise_id: str | None = Form(None),
    difficulty: str = Form("beginner"),
    reference_audio_id: str | None = Form(None),
    enable_phoneme_analysis: bool = Form(True),
    enable_syllable_analysis: bool = Form(True),
    enable_stress_analysis: bool = Form(True),
    container: ServiceContainer = Depends(services),
    _: None = Depends(require_internal_gateway),
) -> PronunciationResponse:
    if difficulty not in {"beginner", "intermediate", "advanced"}:
        raise SpeechError("invalid_difficulty", "difficulty 必须是 beginner、intermediate 或 advanced")
    raw = await save_upload(audio, container.settings)
    try:
        with temporary_directory(container.settings, "pronunciation-") as work:
            info = await asyncio.to_thread(container.audio.normalize, raw, work / "normalized.wav")
            return await asyncio.to_thread(
                container.pronunciation.analyze, info, target_text, difficulty
            )
    finally:
        raw.unlink(missing_ok=True)
