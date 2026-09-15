export interface BatchDeleteFailure {
  id: string;
  code: string;
  reason: string;
}

export interface BatchDeleteResult {
  deleted: number;
  failures: BatchDeleteFailure[];
}
