import secrets
from typing import Annotated

from fastapi import Depends, Header

from app.api.dependencies import settings_from_request
from app.core.errors import InternalAuthenticationError, ServiceNotConfiguredError
from app.core.settings import Settings


async def require_internal_service(
    settings: Annotated[Settings, Depends(settings_from_request)],
    credential: Annotated[str | None, Header(alias="X-Service-Authorization")] = None,
) -> None:
    if not settings.internal_api_configured:
        raise ServiceNotConfiguredError("internal service authentication")
    expected = settings.internal_service_token
    if (
        expected is None
        or credential is None
        or not secrets.compare_digest(
            credential,
            expected.get_secret_value(),
        )
    ):
        raise InternalAuthenticationError()
