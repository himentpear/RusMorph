import secrets

from fastapi import Header, Request

from app.services.container import ServiceContainer
from app.utils.exceptions import SpeechError


def services(request: Request) -> ServiceContainer:
    return request.app.state.services


def require_internal_gateway(
    request: Request,
    x_rusmorph_internal_token: str | None = Header(default=None),
) -> None:
    settings = services(request).settings
    if settings.environment.strip().lower() != "production":
        return
    expected = settings.internal_api_token
    if not expected:
        raise SpeechError("internal_auth_not_configured", "精评服务暂未配置", 503)
    if not x_rusmorph_internal_token or not secrets.compare_digest(x_rusmorph_internal_token, expected):
        raise SpeechError("internal_auth_required", "精评服务只接受网关请求", 401)
