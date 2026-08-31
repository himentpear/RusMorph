from fastapi import Request

from app.services.container import ServiceContainer


def services(request: Request) -> ServiceContainer:
    return request.app.state.services
