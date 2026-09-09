from typing import Literal

from app.schemas.common import ApiModel


class HealthResponse(ApiModel):
    status: Literal["ok"] = "ok"
    service: str
    environment: str
    ready: bool
    internal_api_configured: bool
    provider_configured: bool
    providers: list[str]
