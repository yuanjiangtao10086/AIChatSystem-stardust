import { PageResult } from "@/types/conversation";
export type MemoryType = "PREFERENCE" | "PROJECT" | "GOAL" | "EXPLICIT";
export interface UserMemory {
  id: string;
  content: string;
  summary: string;
  memoryType: MemoryType;
  importance: number;
  enabled: boolean;
  origin: "MANUAL" | "AUTO";
  sourceConversationId?: string;
  sourceMessageId?: string;
  createdAt: string;
  updatedAt: string;
}
export interface MemoryInput {
  content: string;
  summary: string;
  memoryType: MemoryType;
  importance: number;
  enabled?: boolean;
}
export type MemoryPage = PageResult<UserMemory>;
