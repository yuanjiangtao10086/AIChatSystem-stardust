import { apiRequest } from "@/api/client";
import { BatchDeleteResult } from "@/types/batch";
import {
  MemoryInput,
  MemoryPage,
  MemoryType,
  UserMemory,
} from "@/types/memory";

export function listMemories(
  query: {
    page?: number;
    size?: number;
    search?: string;
    enabled?: boolean;
    type?: MemoryType;
  } = {}
): Promise<MemoryPage> {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
  });
  if (query.search?.trim()) params.set("search", query.search.trim());
  if (query.enabled !== undefined) {
    params.set("enabled", String(query.enabled));
  }
  if (query.type) params.set("type", query.type);
  return apiRequest(`/api/v1/memories?${params}`);
}

export function createMemory(input: MemoryInput): Promise<UserMemory> {
  return apiRequest("/api/v1/memories", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateMemory(
  id: string,
  input: Partial<MemoryInput>
): Promise<UserMemory> {
  return apiRequest(`/api/v1/memories/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

export function setMemoryEnabled(
  id: string,
  enabled: boolean
): Promise<UserMemory> {
  return apiRequest(`/api/v1/memories/${encodeURIComponent(id)}/enabled`, {
    method: "PATCH",
    body: JSON.stringify({ enabled }),
  });
}

export function deleteMemory(id: string): Promise<void> {
  return apiRequest(`/api/v1/memories/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function deleteMemoriesBatch(ids: string[]): Promise<BatchDeleteResult> {
  return apiRequest("/api/v1/memories/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
}
