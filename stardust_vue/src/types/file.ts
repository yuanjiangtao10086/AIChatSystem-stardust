import { PageResult } from "@/types/conversation";

export type UserFileStatus =
  | "UPLOADING"
  | "AVAILABLE"
  | "DELETING"
  | "FAILED"
  | "DELETED";

export interface FileReference {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  previewable: boolean;
  downloadUrl: string;
  previewUrl?: string;
}

export interface UserFile extends FileReference {
  detectedMimeType: string;
  extension: string;
  sha256: string;
  storageProvider: string;
  status: UserFileStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StorageUsage {
  usedBytes: number;
  reservedBytes: number;
  quotaBytes: number;
  fileCount: number;
}

export interface FileQuery {
  page?: number;
  size?: number;
  search?: string;
}

export type FilePage = PageResult<UserFile>;
