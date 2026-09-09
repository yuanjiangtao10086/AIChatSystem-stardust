from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True, slots=True)
class VectorRecord:
    id: str
    user_id: str
    knowledge_base_id: str
    document_id: str
    chunk_index: int
    content: str
    token_count: int
    vector: tuple[float, ...]
    page: int | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True, slots=True)
class VectorMatch:
    record: VectorRecord
    score: float


class VectorStore(ABC):
    @abstractmethod
    async def replace_document(self, records: tuple[VectorRecord, ...]) -> None: ...

    @abstractmethod
    async def search(
        self,
        user_id: str,
        knowledge_base_ids: tuple[str, ...],
        query_vector: tuple[float, ...],
        limit: int,
    ) -> tuple[VectorMatch, ...]: ...

    @abstractmethod
    async def delete_document(
        self, user_id: str, knowledge_base_id: str, document_id: str
    ) -> None: ...
