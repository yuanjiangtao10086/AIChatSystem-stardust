import asyncio
import json
import math
import sqlite3
from pathlib import Path

from app.vectorstores.base import VectorMatch, VectorRecord, VectorStore


class SQLiteVectorStore(VectorStore):
    """Persistent local adapter for the first single-instance deployment."""

    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = asyncio.Lock()
        self._connection.execute("PRAGMA journal_mode=WAL")
        self._connection.execute("PRAGMA synchronous=NORMAL")
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS vector_record (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                knowledge_base_id TEXT NOT NULL,
                document_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                content TEXT NOT NULL,
                token_count INTEGER NOT NULL,
                vector_json TEXT NOT NULL,
                page INTEGER,
                metadata_json TEXT NOT NULL
            )
            """
        )
        self._connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_vector_scope "
            "ON vector_record(user_id, knowledge_base_id, document_id)"
        )
        self._connection.commit()

    async def replace_document(self, records: tuple[VectorRecord, ...]) -> None:
        if not records:
            return
        first = records[0]
        if any(
            item.user_id != first.user_id
            or item.knowledge_base_id != first.knowledge_base_id
            or item.document_id != first.document_id
            for item in records
        ):
            raise ValueError("replacement records must share one owner and document scope")
        async with self._lock:
            with self._connection:
                self._connection.execute(
                    "DELETE FROM vector_record WHERE user_id=? AND knowledge_base_id=? "
                    "AND document_id=?",
                    (first.user_id, first.knowledge_base_id, first.document_id),
                )
                self._connection.executemany(
                    """
                    INSERT INTO vector_record (
                        id,user_id,knowledge_base_id,document_id,chunk_index,content,
                        token_count,vector_json,page,metadata_json
                    ) VALUES (?,?,?,?,?,?,?,?,?,?)
                    """,
                    [
                        (
                            item.id,
                            item.user_id,
                            item.knowledge_base_id,
                            item.document_id,
                            item.chunk_index,
                            item.content,
                            item.token_count,
                            json.dumps(item.vector, separators=(",", ":")),
                            item.page,
                            json.dumps(item.metadata, separators=(",", ":"), ensure_ascii=False),
                        )
                        for item in records
                    ],
                )

    async def search(
        self,
        user_id: str,
        knowledge_base_ids: tuple[str, ...],
        query_vector: tuple[float, ...],
        limit: int,
    ) -> tuple[VectorMatch, ...]:
        if not knowledge_base_ids:
            return ()
        placeholders = ",".join("?" for _ in knowledge_base_ids)
        async with self._lock:
            rows = self._connection.execute(
                f"SELECT * FROM vector_record WHERE user_id=? "
                f"AND knowledge_base_id IN ({placeholders})",  # noqa: S608 - placeholders only
                (user_id, *knowledge_base_ids),
            ).fetchall()
        matches = [
            VectorMatch(
                self._record(row),
                self._cosine(query_vector, tuple(json.loads(row["vector_json"]))),
            )
            for row in rows
        ]
        matches.sort(key=lambda item: item.score, reverse=True)
        return tuple(matches[:limit])

    async def delete_document(self, user_id: str, knowledge_base_id: str, document_id: str) -> None:
        async with self._lock:
            with self._connection:
                self._connection.execute(
                    "DELETE FROM vector_record WHERE user_id=? AND knowledge_base_id=? "
                    "AND document_id=?",
                    (user_id, knowledge_base_id, document_id),
                )

    async def aclose(self) -> None:
        async with self._lock:
            self._connection.close()

    def _record(self, row: sqlite3.Row) -> VectorRecord:
        return VectorRecord(
            id=row["id"], user_id=row["user_id"], knowledge_base_id=row["knowledge_base_id"],
            document_id=row["document_id"], chunk_index=row["chunk_index"], content=row["content"],
            token_count=row["token_count"], vector=tuple(json.loads(row["vector_json"])),
            page=row["page"], metadata=json.loads(row["metadata_json"]),
        )

    def _cosine(self, left: tuple[float, ...], right: tuple[float, ...]) -> float:
        if len(left) != len(right) or not left:
            return 0.0
        dot = sum(a * b for a, b in zip(left, right, strict=True))
        norm = math.sqrt(sum(value * value for value in left)) * math.sqrt(
            sum(value * value for value in right)
        )
        return dot / norm if norm else 0.0
