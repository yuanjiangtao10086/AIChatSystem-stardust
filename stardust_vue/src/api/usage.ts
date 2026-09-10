import { apiRequest } from "@/api/client";
import {
  BreakdownDimension,
  UsageBreakdownItem,
  UsageView,
} from "@/types/usage";

const query = (
  path: string,
  values: Record<string, string | number | undefined>
) => {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== "") p.set(k, String(v));
  });
  const qs = p.toString();
  return qs ? `${path}?${qs}` : path;
};

export const getUsage = (): Promise<UsageView> =>
  apiRequest<UsageView>("/api/v1/usage");

export const getUsageBreakdown = (
  by: BreakdownDimension = "DAY",
  from?: string,
  to?: string
): Promise<UsageBreakdownItem[]> =>
  apiRequest<UsageBreakdownItem[]>(
    query("/api/v1/usage/breakdown", { by, from, to })
  );

export const getAdminUsageBreakdown = (
  by: BreakdownDimension = "DAY",
  from?: string,
  to?: string
): Promise<UsageBreakdownItem[]> =>
  apiRequest<UsageBreakdownItem[]>(
    query("/api/v1/admin/usage/breakdown", { by, from, to })
  );
