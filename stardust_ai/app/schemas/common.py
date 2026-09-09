import re
from typing import Any

from pydantic import BaseModel, ConfigDict


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class ErrorResponse(ApiModel):
    code: str
    message: str
    request_id: str
    retryable: bool = False
    details: list[dict[str, Any]] | None = None


AI_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
