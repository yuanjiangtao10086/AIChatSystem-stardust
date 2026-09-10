export interface ValidationError {
  field: string;
  reason: string;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  requestId: string | null;
  timestamp: string;
  errors?: ValidationError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: number;
  readonly requestId: string | null;
  readonly errors: ValidationError[];

  constructor(status: number, result: ApiResult<unknown>) {
    super(result.message);
    this.name = "ApiError";
    this.status = status;
    this.code = result.code;
    this.requestId = result.requestId;
    this.errors = result.errors ?? [];
  }
}

let accessToken: string | null = null;
let refreshHandler: (() => Promise<string | null>) | null = null;
const PUBLIC_AUTH_PATHS = new Set([
  "/api/v1/auth/register",
  "/api/v1/auth/login",
  "/api/v1/auth/refresh",
]);

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function setRefreshHandler(handler: () => Promise<string | null>): void {
  refreshHandler = handler;
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  retryAfterRefresh = true
): Promise<T> {
  const response = await authenticatedFetch(path, init, retryAfterRefresh);

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  const result = (await response.json()) as ApiResult<T>;
  if (!response.ok || result.code !== 0) {
    throw new ApiError(response.status, result as ApiResult<unknown>);
  }
  return result.data;
}

export async function authenticatedFetch(
  path: string,
  init: RequestInit = {},
  retryAfterRefresh = true
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken && !PUBLIC_AUTH_PATHS.has(path)) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: "same-origin",
  });

  if (response.status === 401 && retryAfterRefresh && refreshHandler) {
    const refreshedToken = await refreshHandler();
    if (refreshedToken) {
      return authenticatedFetch(path, init, false);
    }
  }
  return response;
}
