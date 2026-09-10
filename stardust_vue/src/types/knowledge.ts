import { PageResult } from "@/types/conversation";

export type KnowledgeDocumentStatus =
  | "UPLOADED"
  | "PARSING"
  | "PARSED"
  | "EMBEDDING"
  | "READY"
  | "FAILED";

export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string;
  status: "ACTIVE" | "DELETED";
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeDocument {
  id: string;
  knowledgeBaseId: string;
  fileId: string;
  fileName: string;
  mimeType: string;
  status: KnowledgeDocumentStatus;
  chunkCount: number;
  processingVersion: number;
  errorCode?: string;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type KnowledgeBasePage = PageResult<KnowledgeBase>;
export type KnowledgeDocumentPage = PageResult<KnowledgeDocument>;

export interface ConversationKnowledgeBases {
  items: KnowledgeBase[];
}
