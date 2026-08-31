from contextlib import asynccontextmanager
import asyncio

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api import asr, examples, health, pronunciation
from app.services.container import ServiceContainer, build_container
from app.utils.exceptions import SpeechError
from app.utils.logging import configure_logging


def create_app(container: ServiceContainer | None = None) -> FastAPI:
    configure_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.services = container or build_container()
        warm_up = getattr(app.state.services.asr, "warm_up", None)
        if app.state.services.settings.asr_warmup and callable(warm_up):
            await asyncio.to_thread(warm_up)
        yield

    app = FastAPI(
        title="RusMorph Russian Speech API",
        version="0.1.0",
        lifespan=lifespan,
    )

    @app.exception_handler(SpeechError)
    async def speech_error(_: Request, exc: SpeechError):
        return JSONResponse(
            status_code=exc.status_code,
            content={"success": False, "error_code": exc.code, "message": exc.message},
        )

    app.include_router(health.router)
    app.include_router(asr.router)
    app.include_router(pronunciation.router)
    app.include_router(examples.router)
    return app


app = create_app()
