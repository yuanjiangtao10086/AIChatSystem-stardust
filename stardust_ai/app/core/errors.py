from dataclasses import dataclass


@dataclass(slots=True)
class AiServiceError(Exception):
    code: str
    message: str
    status_code: int
    retryable: bool = False

    def __str__(self) -> str:
        return self.message


class InternalAuthenticationError(AiServiceError):
    def __init__(self) -> None:
        super().__init__("INTERNAL_UNAUTHORIZED", "internal service authentication failed", 401)


class ServiceNotConfiguredError(AiServiceError):
    def __init__(self, component: str) -> None:
        super().__init__("SERVICE_NOT_CONFIGURED", f"{component} is not configured", 503)


class ProviderNotFoundError(AiServiceError):
    def __init__(self, provider_key: str) -> None:
        super().__init__("PROVIDER_NOT_FOUND", f"provider '{provider_key}' is not configured", 400)


class ProviderError(AiServiceError):
    pass


class ProviderTimeoutError(ProviderError):
    def __init__(self) -> None:
        super().__init__("PROVIDER_TIMEOUT", "provider request timed out", 504, retryable=True)


class ProviderUnavailableError(ProviderError):
    def __init__(self) -> None:
        super().__init__("PROVIDER_UNAVAILABLE", "provider is unavailable", 502, retryable=True)


class ProviderRateLimitedError(ProviderError):
    def __init__(self) -> None:
        super().__init__("PROVIDER_RATE_LIMITED", "provider rate limit exceeded", 429, True)


class ProviderRequestError(ProviderError):
    def __init__(self) -> None:
        super().__init__("PROVIDER_REQUEST_REJECTED", "provider rejected the request", 502)


class ProviderAuthenticationError(ProviderError):
    def __init__(self) -> None:
        super().__init__(
            "PROVIDER_AUTHENTICATION_FAILED",
            "provider rejected the configured credential",
            502,
        )


class ProviderResourceNotFoundError(ProviderError):
    def __init__(self) -> None:
        super().__init__(
            "PROVIDER_RESOURCE_NOT_FOUND",
            "provider model or resource does not exist",
            502,
        )


class ProviderProtocolError(ProviderError):
    def __init__(self) -> None:
        super().__init__("PROVIDER_PROTOCOL_ERROR", "provider returned an invalid response", 502)


class VectorStoreError(AiServiceError):
    def __init__(self) -> None:
        super().__init__("VECTOR_STORE_ERROR", "vector store operation failed", 500)


class ArtifactError(AiServiceError):
    """A downloadable-file generation failed.

    Carries a stable error ``code`` the streaming layer maps to an ``artifact_error`` SSE
    event so a bad file never fails the whole chat. Status defaults to 500; size/type
    violations use 413/415 so the Spring side can mirror the HTTP semantics.
    """

    def __init__(
        self,
        code: str,
        message: str,
        status_code: int = 500,
        retryable: bool = False,
    ) -> None:
        super().__init__(code, message, status_code, retryable)
