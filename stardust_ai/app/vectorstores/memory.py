import asyncio
import math

from app.vectorstores.base import VectorMatch, VectorRecord, VectorStore


class InMemoryVectorStore(VectorStore):
    """Replaceable development adapter; namespaces every operation by owner and KB."""

    def __init__(self) -> None:
        self._records: dict[str, VectorRecord] = {}
        self._lock = asyncio.Lock()

    async def replace_document(self, records: tuple[VectorRecord, ...]) -> None:
        if not records:
            return
        first = records[0]
        async with self._lock:
            self._records = {
                key: value
                for key, value in self._records.items()
                if not (
                    value.user_id == first.user_id
                    and value.knowledge_base_id == first.knowledge_base_id
                    and value.document_id == first.document_id
                )
            }
            self._records.update({record.id: record for record in records})

    async def search(
        self,
        user_id: str,
        knowledge_base_ids: tuple[str, ...],
        query_vector: tuple[float, ...],
        limit: int,
    ) -> tuple[VectorMatch, ...]:
        allowed = set(knowledge_base_ids)
        async with self._lock:
            candidates = tuple(self._records.values())
        matches = [
            VectorMatch(record, self._cosine(query_vector, record.vector))
            for record in candidates
            if record.user_id == user_id and record.knowledge_base_id in allowed
        ]
        matches.sort(key=lambda item: item.score, reverse=True)
        return tuple(matches[:limit])

    async def delete_document(self, user_id: str, knowledge_base_id: str, document_id: str) -> None:
        async with self._lock:
            self._records = {
                key: value
                for key, value in self._records.items()
                if not (
                    value.user_id == user_id
                    and value.knowledge_base_id == knowledge_base_id
                    and value.document_id == document_id
                )
            }

    def _cosine(self, left: tuple[float, ...], right: tuple[float, ...]) -> float:
        if len(left) != len(right) or not left:
            return 0.0
        dot = sum(a * b for a, b in zip(left, right, strict=True))
        norm = math.sqrt(sum(value * value for value in left)) * math.sqrt(
            sum(value * value for value in right)
        )
        return dot / norm if norm else 0.0
