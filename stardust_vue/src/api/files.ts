import {
  apiRequest,
  authenticatedFetch,
  ApiResult,
  ApiError,
} from "@/api/client";
import {
  FilePage,
  FileQuery,
  FileReference,
  StorageUsage,
  UserFile,
} from "@/types/file";
import { BatchDeleteResult } from "@/types/batch";

export function listFiles(query: FileQuery = {}): Promise<FilePage> {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
  });
  if (query.search?.trim()) params.set("search", query.search.trim());
  return apiRequest(`/api/v1/files?${params.toString()}`);
}

export function getStorageUsage(): Promise<StorageUsage> {
  return apiRequest("/api/v1/files/usage");
}

export function getFile(id: string): Promise<UserFile> {
  return apiRequest(`/api/v1/files/${encodeURIComponent(id)}`);
}

export function uploadFile(file: File): Promise<UserFile> {
  const body = new FormData();
  body.append("file", file);
  return apiRequest("/api/v1/files", { method: "POST", body });
}

export function renameFile(id: string, name: string): Promise<UserFile> {
  return apiRequest(`/api/v1/files/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify({ name }),
  });
}

export function deleteFile(id: string): Promise<void> {
  return apiRequest(`/api/v1/files/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function deleteFilesBatch(ids: string[]): Promise<BatchDeleteResult> {
  return apiRequest("/api/v1/files/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
}

export async function fetchFileBlob(
  file: FileReference,
  preview = false
): Promise<Blob> {
  const path = preview && file.previewUrl ? file.previewUrl : file.downloadUrl;
  const response = await authenticatedFetch(path);
  if (!response.ok) {
    const result = (await response.json()) as ApiResult<unknown>;
    throw new ApiError(response.status, result);
  }
  return response.blob();
}

export async function downloadFile(file: FileReference): Promise<void> {
  const blob = await fetchFileBlob(file);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = file.name;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let size = value / 1024;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index++;
  }
  return `${size >= 10 ? size.toFixed(1) : size.toFixed(2)} ${units[index]}`;
}
