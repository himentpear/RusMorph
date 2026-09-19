import asyncio
import json
import secrets
import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, UploadFile, WebSocket, WebSocketDisconnect

from app.api.dependencies import require_internal_gateway, services
from app.schemas.asr import TranscriptionResponse
from app.services.asr_service import normalize_transcript
from app.services.container import ServiceContainer
from app.utils.exceptions import SpeechError
from app.utils.files import save_upload, temporary_directory

router = APIRouter(prefix="/api/asr", tags=["asr"])


@router.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe(
    audio: UploadFile = File(...),
    language: str = Form("ru"),
    enable_punctuation: bool = Form(True),
    enable_word_timestamps: bool = Form(True),
    search_mode: bool = Form(True),
    container: ServiceContainer = Depends(services),
    _: None = Depends(require_internal_gateway),
) -> TranscriptionResponse:
    raw = await save_upload(audio, container.settings)
    try:
        with temporary_directory(container.settings, "asr-") as work:
            normalized = work / "normalized.wav"
            info = await asyncio.to_thread(container.audio.normalize, raw, normalized)
            warnings = container.vad.validate(info)
            result = await asyncio.to_thread(container.asr.transcribe, normalized, language)
            if result.language != "ru" and result.language_confidence >= 0.65:
                warnings.append("识别语言不是俄语")
            if result.confidence < container.settings.confirm_search_threshold:
                warnings.append("识别置信度较低，请确认候选文本或重新录音")
            should_auto_search = (
                search_mode
                and result.language == "ru"
                and result.confidence >= container.settings.auto_search_threshold
            )
            return TranscriptionResponse(
                language=result.language, language_confidence=result.language_confidence,
                transcript=result.text, normalized_transcript=normalize_transcript(result.text),
                confidence=result.confidence, should_auto_search=should_auto_search,
                duration_ms=info.duration_ms, speech_duration_ms=info.speech_duration_ms,
                words=result.words if enable_word_timestamps else [],
                alternatives=result.alternatives, warnings=warnings,
            )
    finally:
        raw.unlink(missing_ok=True)


@router.websocket("/stream")
async def stream(websocket: WebSocket):
    container: ServiceContainer = websocket.app.state.services
    settings = container.settings
    if settings.environment.strip().lower() == "production":
        expected = settings.internal_api_token
        provided = websocket.headers.get("x-rusmorph-internal-token")
        if not expected:
            await websocket.close(code=1013)
            return
        if not provided or not secrets.compare_digest(provided, expected):
            await websocket.close(code=1008)
            return
    await websocket.accept()
    buffer = bytearray()
    suffix = ".webm"
    started = False

    async def recognize(final: bool) -> None:
        if not buffer:
            raise SpeechError("empty_audio", "尚未收到音频分片")
        with tempfile.NamedTemporaryFile(
            prefix="stream-", suffix=suffix, dir=container.settings.temp_dir, delete=False
        ) as target:
            target.write(buffer)
            raw = Path(target.name)
        try:
            with temporary_directory(container.settings, "stream-asr-") as work:
                info = await asyncio.to_thread(container.audio.normalize, raw, work / "normalized.wav")
                result = await asyncio.to_thread(container.asr.transcribe, info.path, "ru")
                if final:
                    await websocket.send_json({
                        "type": "final", "text": result.text, "confidence": result.confidence,
                        "should_auto_search": result.language == "ru"
                        and result.confidence >= container.settings.auto_search_threshold,
                    })
                else:
                    await websocket.send_json({
                        "type": "partial", "text": result.text, "confidence": result.confidence,
                    })
        finally:
            raw.unlink(missing_ok=True)

    try:
        while True:
            message = await asyncio.wait_for(
                websocket.receive(), timeout=container.settings.websocket_timeout_seconds
            )
            if message.get("bytes") is not None:
                if not started:
                    await websocket.send_json({"type": "error", "code": "not_started"})
                    continue
                buffer.extend(message["bytes"])
                if len(buffer) > container.settings.max_upload_bytes:
                    raise SpeechError("audio_too_large", "流式音频超过大小限制")
                continue
            raw_text = message.get("text")
            if raw_text is None:
                continue
            try:
                command = json.loads(raw_text)
            except json.JSONDecodeError:
                await websocket.send_json({"type": "error", "code": "invalid_command"})
                continue
            if not isinstance(command, dict):
                await websocket.send_json({"type": "error", "code": "invalid_command"})
                continue
            action = command.get("type")
            if action == "start":
                buffer.clear()
                suffix = command.get("format", "webm")
                suffix = "." + suffix.lstrip(".")
                if suffix not in container.settings.allowed_extensions:
                    raise SpeechError("unsupported_audio_format", "不支持的流式音频格式")
                started = True
                await websocket.send_json({"type": "started"})
            elif action == "partial":
                await recognize(False)
            elif action == "stop":
                await recognize(True)
                buffer.clear()
                started = False
            elif action == "cancel":
                buffer.clear()
                started = False
                await websocket.send_json({"type": "cancelled"})
            else:
                await websocket.send_json({"type": "error", "code": "unknown_command"})
    except asyncio.TimeoutError:
        await websocket.send_json({"type": "error", "code": "timeout", "message": "识别超时"})
        await websocket.close(code=1000)
    except WebSocketDisconnect:
        pass
    except SpeechError as exc:
        await websocket.send_json({"type": "error", "code": exc.code, "message": exc.message})
        await websocket.close(code=1003)
    finally:
        buffer.clear()
