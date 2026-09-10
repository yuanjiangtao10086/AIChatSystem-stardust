import { apiRequest } from "@/api/client";
import {
  ConversationKnowledgeBases,
  KnowledgeBase,
  KnowledgeBasePage,
  KnowledgeDocument,
  KnowledgeDocumentPage,
} from "@/types/knowledge";

export function listKnowledgeBases(
  query: { page?: number; size?: number; search?: string } = {}
): Promise<KnowledgeBasePage> {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
  });
  if (query.search?.trim()) params.set("search", query.search.trim());
  return apiRequest(`/api/v1/knowledge-bases?${params}`);
}

export function createKnowledgeBase(
  name: string,
  description?: string
): Promise<KnowledgeBase> {
  return apiRequest("/api/v1/knowledge-bases", {
    method: "POST",
    body: JSON.stringify({ name, description: description || null }),
  });
}

export function updateKnowledgeBase(
  id: string,
  input: { name?: string; description?: string }
): Promise<KnowledgeBase> {
  return apiRequest(`/api/v1/knowledge-bases/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function deleteKnowledgeBase(id: string): Promise<void> {
  return apiRequest(`/api/v1/knowledge-bases/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function listKnowledgeDocuments(
  id: string,
  page = 0
): Promise<KnowledgeDocumentPage> {
  return apiRequest(
    `/api/v1/knowledge-bases/${encodeURIComponent(
      id
    )}/documents?page=${page}&size=50`
  );
}

export function addKnowledgeDocument(
  id: string,
  fileId: string
): Promise<KnowledgeDocument> {
  return apiRequest(
    `/api/v1/knowledge-bases/${encodeURIComponent(id)}/documents`,
    {
      method: "POST",
      body: JSON.stringify({ fileId }),
    }
  );
}

export function retryKnowledgeDocument(id: string): Promise<KnowledgeDocument> {
  return apiRequest(
    `/api/v1/knowledge-documents/${encodeURIComponent(id)}/retry`,
    { method: "POST" }
  );
}

export function deleteKnowledgeDocument(id: string): Promise<void> {
  return apiRequest(`/api/v1/knowledge-documents/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function getConversationKnowledgeBases(
  id: string
): Promise<ConversationKnowledgeBases> {
  return apiRequest(
    `/api/v1/conversations/${encodeURIComponent(id)}/knowledge-bases`
  );
}

export function setConversationKnowledgeBases(
  id: string,
  knowledgeBaseIds: string[]
): Promise<ConversationKnowledgeBases> {
  return apiRequest(
    `/api/v1/conversations/${encodeURIComponent(id)}/knowledge-bases`,
    {
      method: "PUT",
      body: JSON.stringify({ knowledgeBaseIds }),
    }
  );
}
