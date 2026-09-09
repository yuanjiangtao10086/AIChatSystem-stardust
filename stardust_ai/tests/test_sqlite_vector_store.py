from pathlib import Path

import pytest

from app.vectorstores.base import VectorRecord
from app.vectorstores.sqlite import SQLiteVectorStore


@pytest.mark.asyncio
async def test_sqlite_store_persists_and_namespaces_by_owner(tmp_path: Path) -> None:
    path = tmp_path / "vectors.sqlite3"
    first = SQLiteVectorStore(path)
    await first.replace_document(
        (
            VectorRecord("doc-a:0", "user-a", "kb-a", "doc-a", 0, "owner text", 3, (1.0, 0.0)),
        )
    )
    await first.replace_document(
        (VectorRecord("doc-b:0", "user-b", "kb-b", "doc-b", 0, "private text", 3, (1.0, 0.0)),)
    )
    await first.aclose()

    reopened = SQLiteVectorStore(path)
    matches = await reopened.search("user-a", ("kb-a", "kb-b"), (1.0, 0.0), 10)
    assert [item.record.document_id for item in matches] == ["doc-a"]
    await reopened.aclose()
