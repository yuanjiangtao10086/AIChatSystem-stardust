export interface UsageView {
  quotaTokens: number;
  usedTokens: number;
  reservedTokens: number;
  availableTokens: number;
  quotaCost: number;
  usedCost: number;
  reservedCost: number;
  currency: string;
  periodStart: string;
  periodEnd: string;
}

export type BreakdownDimension = "DAY" | "MODEL" | "PROVIDER";

export interface UsageBreakdownItem {
  key: string;
  requestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}
