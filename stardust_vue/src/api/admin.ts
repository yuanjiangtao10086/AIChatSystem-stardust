import {
  ApiError,
  ApiResult,
  apiRequest,
  authenticatedFetch,
} from "@/api/client";
import * as T from "@/types/admin";
import { BatchDeleteResult } from "@/types/batch";
import { UserRole, UserStatus } from "@/types/auth";

const query = (
  path: string,
  values: Record<string, string | number | undefined>
) => {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== "") p.set(k, String(v));
  });
  return `${path}?${p}`;
};
export const getDashboard = () =>
  apiRequest<T.AdminDashboard>("/api/v1/admin/dashboard");
export const listAdminUsers = (
  page = 0,
  search = "",
  status = "",
  role = "",
  direction: "ASC" | "DESC" | "" = ""
) =>
  apiRequest<T.AdminUserPage>(
    query("/api/v1/admin/users", {
      page,
      size: 20,
      search,
      status,
      role,
      direction,
    })
  );
export const getAdminUser = (id: string) =>
  apiRequest<T.AdminUser>(`/api/v1/admin/users/${encodeURIComponent(id)}`);
export const createAdminUser = (body: {
  email: string;
  displayName: string;
  password: string;
  roles: UserRole[];
}) =>
  apiRequest<T.AdminUser>("/api/v1/admin/users", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const updateAdminUser = (
  id: string,
  body: { email: string; displayName: string }
) =>
  apiRequest<T.AdminUser>(`/api/v1/admin/users/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
export const setAdminUserStatus = (
  id: string,
  status: UserStatus,
  reason?: string
) =>
  apiRequest<T.AdminUser>(
    `/api/v1/admin/users/${encodeURIComponent(id)}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status, reason: reason || null }),
    }
  );
export const restoreAdminUser = (id: string) =>
  apiRequest<T.AdminUser>(
    `/api/v1/admin/users/${encodeURIComponent(id)}/restore`,
    { method: "POST" }
  );
export const resetAdminPassword = (id: string, password: string) =>
  apiRequest<void>(
    `/api/v1/admin/users/${encodeURIComponent(id)}/reset-password`,
    { method: "POST", body: JSON.stringify({ password }) }
  );
export const setAdminUserRoles = (id: string, roles: UserRole[]) =>
  apiRequest<T.AdminUser>(
    `/api/v1/admin/users/${encodeURIComponent(id)}/roles`,
    { method: "PUT", body: JSON.stringify({ roles }) }
  );
export const adjustAdminUsage = (
  id: string,
  body: { tokenDelta: number; costDelta: string; reason: string }
) =>
  apiRequest<T.AdminUser>(
    `/api/v1/admin/users/${encodeURIComponent(id)}/usage/adjust`,
    { method: "POST", body: JSON.stringify(body) }
  );
export const deleteAdminUser = (id: string) =>
  apiRequest<void>(`/api/v1/admin/users/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const deleteAdminUsersBatch = (ids: string[]) =>
  apiRequest<BatchDeleteResult>("/api/v1/admin/users/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
export const listAdminConversations = (
  params: {
    page?: number;
    search?: string;
    userId?: string;
    from?: string;
    to?: string;
  } = {}
) =>
  apiRequest<T.AdminConversationPage>(
    query("/api/v1/admin/conversations", {
      page: params.page ?? 0,
      size: 20,
      search: params.search ?? "",
      userId: params.userId ?? "",
      from: params.from ?? "",
      to: params.to ?? "",
    })
  );
export const getAdminConversation = (id: string) =>
  apiRequest<T.AdminConversationDetail>(
    `/api/v1/admin/conversations/${encodeURIComponent(id)}`
  );
export const listAdminMessages = (id: string, page = 0) =>
  apiRequest<T.AdminMessagePage>(
    query(`/api/v1/admin/conversations/${encodeURIComponent(id)}/messages`, {
      page,
      size: 50,
    })
  );
export const deleteAdminConversation = (id: string) =>
  apiRequest<void>(`/api/v1/admin/conversations/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const deleteAdminConversationsBatch = (ids: string[]) =>
  apiRequest<BatchDeleteResult>("/api/v1/admin/conversations/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
export const deleteAdminMessage = (id: string) =>
  apiRequest<void>(`/api/v1/admin/messages/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const listAdminFiles = (
  params: {
    page?: number;
    search?: string;
    userSearch?: string;
    mime?: string;
    status?: string;
    userId?: string;
    minSize?: string;
    maxSize?: string;
    from?: string;
    to?: string;
  } = {}
) =>
  apiRequest<T.AdminFilePage>(
    query("/api/v1/admin/files", {
      page: params.page ?? 0,
      size: 20,
      search: params.search ?? "",
      userSearch: params.userSearch ?? "",
      mime: params.mime ?? "",
      status: params.status ?? "",
      userId: params.userId ?? "",
      minSize: params.minSize ?? "",
      maxSize: params.maxSize ?? "",
      from: params.from ?? "",
      to: params.to ?? "",
    })
  );
export const getAdminFile = (id: string) =>
  apiRequest<T.AdminFileDetail>(
    `/api/v1/admin/files/${encodeURIComponent(id)}`
  );
export const deleteAdminFile = (id: string) =>
  apiRequest<void>(`/api/v1/admin/files/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const deleteAdminFilesBatch = (ids: string[]) =>
  apiRequest<BatchDeleteResult>("/api/v1/admin/files/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
/**
 * Downloads through the audited admin endpoint. The bearer token is attached by
 * {@link authenticatedFetch}; no direct storage path is ever built on the client.
 */
export const downloadAdminFile = async (
  id: string,
  name: string
): Promise<void> => {
  const response = await authenticatedFetch(
    `/api/v1/admin/files/${encodeURIComponent(id)}/download`
  );
  if (!response.ok) {
    throw new ApiError(
      response.status,
      (await response.json()) as ApiResult<unknown>
    );
  }
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
};
export const listAdminKnowledgeBases = (
  params: {
    page?: number;
    search?: string;
    userSearch?: string;
    status?: string;
  } = {}
) =>
  apiRequest<T.AdminKnowledgeBasePage>(
    query("/api/v1/admin/knowledge-bases", {
      page: params.page ?? 0,
      size: 20,
      search: params.search ?? "",
      userSearch: params.userSearch ?? "",
      status: params.status ?? "",
    })
  );
export const getAdminKnowledgeBase = (id: string) =>
  apiRequest<T.AdminKnowledgeBaseDetail>(
    `/api/v1/admin/knowledge-bases/${encodeURIComponent(id)}`
  );
export const listAdminKnowledgeDocuments = (
  params: {
    page?: number;
    knowledgeBaseId?: string;
    userId?: string;
    userSearch?: string;
    search?: string;
    status?: string;
  } = {}
) =>
  apiRequest<T.AdminKnowledgeDocumentPage>(
    query("/api/v1/admin/knowledge-documents", {
      page: params.page ?? 0,
      size: 20,
      knowledgeBaseId: params.knowledgeBaseId ?? "",
      userId: params.userId ?? "",
      userSearch: params.userSearch ?? "",
      search: params.search ?? "",
      status: params.status ?? "",
    })
  );
/**
 * Re-processing and vector removal are executed by Spring, which is the only side that
 * talks to the Python AI service; the browser never calls Python.
 */
export const retryAdminDocument = (id: string) =>
  apiRequest<T.AdminKnowledgeDocument>(
    `/api/v1/admin/knowledge-documents/${encodeURIComponent(id)}/retry`,
    { method: "POST" }
  );
export const deleteAdminDocument = (id: string) =>
  apiRequest<void>(
    `/api/v1/admin/knowledge-documents/${encodeURIComponent(id)}`,
    { method: "DELETE" }
  );
export const deleteAdminDocumentsBatch = (ids: string[]) =>
  apiRequest<BatchDeleteResult>("/api/v1/admin/knowledge-documents/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
export const removeAdminVectors = (id: string) =>
  apiRequest<T.AdminKnowledgeDocument>(
    `/api/v1/admin/knowledge-documents/${encodeURIComponent(id)}/vectors`,
    { method: "DELETE" }
  );
export const listAdminProviders = () =>
  apiRequest<T.AdminProvider[]>("/api/v1/admin/ai/providers");
export const saveAdminProvider = (body: T.AdminProviderPayload, id?: string) =>
  apiRequest<T.AdminProvider>(
    id
      ? `/api/v1/admin/ai/providers/${encodeURIComponent(id)}`
      : "/api/v1/admin/ai/providers",
    { method: id ? "PUT" : "POST", body: JSON.stringify(body) }
  );
export const setAdminProviderStatus = (id: string, enabled: boolean) =>
  apiRequest<T.AdminProvider>(
    `/api/v1/admin/ai/providers/${encodeURIComponent(id)}/status`,
    { method: "PATCH", body: JSON.stringify({ enabled }) }
  );
export const deleteAdminProvider = (id: string) =>
  apiRequest<void>(`/api/v1/admin/ai/providers/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const listAdminModels = () =>
  apiRequest<T.AdminModel[]>("/api/v1/admin/ai/models");
export const saveAdminModel = (body: T.AdminModelPayload, id?: string) =>
  apiRequest<T.AdminModel>(
    id
      ? `/api/v1/admin/ai/models/${encodeURIComponent(id)}`
      : "/api/v1/admin/ai/models",
    { method: id ? "PUT" : "POST", body: JSON.stringify(body) }
  );
export const setAdminModelStatus = (id: string, enabled: boolean) =>
  apiRequest<T.AdminModel>(
    `/api/v1/admin/ai/models/${encodeURIComponent(id)}/status`,
    { method: "PATCH", body: JSON.stringify({ enabled }) }
  );
export const setAdminModelDefault = (id: string, defaultModel: boolean) =>
  apiRequest<T.AdminModel>(
    `/api/v1/admin/ai/models/${encodeURIComponent(id)}/default`,
    { method: "PATCH", body: JSON.stringify({ defaultModel }) }
  );
/** Moves a model one slot up/down; the API answers with the provider's reordered catalog. */
export const moveAdminModel = (id: string, direction: "UP" | "DOWN") =>
  apiRequest<T.AdminModel[]>(
    `/api/v1/admin/ai/models/${encodeURIComponent(id)}/order`,
    { method: "PATCH", body: JSON.stringify({ direction }) }
  );
export const deleteAdminModel = (id: string) =>
  apiRequest<void>(`/api/v1/admin/ai/models/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
export const listAdminAiRequests = (params: T.AdminAiRequestQuery = {}) =>
  apiRequest<T.AdminAiRequestPage>(
    query("/api/v1/admin/ai/requests", { size: 20, ...params })
  );
export const getAdminAiRequest = (requestId: string) =>
  apiRequest<T.AdminAiRequest>(
    `/api/v1/admin/ai/requests/${encodeURIComponent(requestId)}`
  );
export const listAdminAuditLogs = (params: T.AdminAuditQuery = {}) =>
  apiRequest<T.AdminAuditPage>(
    query("/api/v1/admin/audit-logs", { size: 20, ...params })
  );
