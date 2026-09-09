import secrets

from fastapi import Header, Request

from app.core.errors import InternalAuthenticationError, ServiceNotConfiguredError


async def require_internal_service(
    request: Request,
    service_credential: str | None = Header(default=None, alias="X-Service-Authorization"),
) -> None:
    settings = request.app.state.settings
    expected = settings.internal_service_token
    if expected is None or not settings.internal_api_configured:
        raise ServiceNotConfiguredError("internal service authentication")
    if service_credential is None or not secrets.compare_digest(
        service_credential, expected.get_secret_value()
    ):
        raise InternalAuthenticationError()
