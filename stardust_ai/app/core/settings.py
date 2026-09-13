from functools import lru_cache
from pathlib import Path

from pydantic import Field, HttpUrl, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="STARDUST_AI_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    service_name: str = "stardust-ai"
    environment: str = "development"
    log_level: str = "INFO"
    internal_service_token: SecretStr | None = None
    provider_timeout_seconds: float = Field(default=60.0, gt=0, le=300)
    provider_stream_read_timeout_seconds: float = Field(default=600.0, gt=0, le=3600)
    # Demo aid only. When > 0, the chat stream is paced by sleeping this many
    # milliseconds between emitted chunks (and large deltas are split into small
    # pieces) so that a very fast provider still renders as visibly incremental
    # text in the UI. Leave at 0 in production: the real network stream is used as-is.
    stream_throttle_ms: float = Field(default=0.0, ge=0, le=200)
    openai_compatible_base_url: HttpUrl = HttpUrl("https://api.openai.com/v1")
    openai_compatible_api_key: SecretStr | None = None
    openai_compatible_default_model: str | None = None
    openai_compatible_embedding_model: str | None = None
    # Chat attachments: Spring ships content, this service only enforces the rendering budget.
    attachment_max_text_chars: int = Field(default=60_000, ge=1_000, le=400_000)
    attachment_image_detail: str = Field(default="auto", pattern="^(auto|low|high)$")
    rag_max_document_bytes: int = Field(default=26_214_400, gt=0, le=52_428_800)
    rag_chunk_chars: int = Field(default=1800, ge=256, le=8000)
    rag_chunk_overlap_chars: int = Field(default=180, ge=0, le=2000)
    rag_vector_store_path: Path = Path("./data/rag-vectors.sqlite3")

    @property
    def internal_api_configured(self) -> bool:
        token = self.internal_service_token
        return token is not None and len(token.get_secret_value()) >= 32

    @property
    def provider_configured(self) -> bool:
        return bool(self.openai_compatible_default_model)


@lru_cache
def get_settings() -> Settings:
    return Settings()
