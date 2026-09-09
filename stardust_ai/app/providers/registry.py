from app.core.errors import ProviderNotFoundError
from app.providers.base import LLMProvider


class ProviderRegistry:
    def __init__(self, providers: dict[str, LLMProvider]) -> None:
        self._providers = dict(providers)

    def get(self, provider_key: str) -> LLMProvider:
        try:
            return self._providers[provider_key]
        except KeyError as error:
            raise ProviderNotFoundError(provider_key) from error

    @property
    def keys(self) -> tuple[str, ...]:
        return tuple(sorted(self._providers))

    async def aclose(self) -> None:
        for provider in self._providers.values():
            close = getattr(provider, "aclose", None)
            if close is not None:
                await close()
