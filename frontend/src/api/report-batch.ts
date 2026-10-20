import request from '@/utils/request';
import type { ApiResponse, PageResult } from '@/types/common';

export type ReportBatchStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'PARTIAL_FAILED' | 'FAILED';
export type ReportBatchItemStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';

export interface ReportBatch {
  id: number;
  classId: number;
  className?: string | null;
  eventId: number;
  eventTitle?: string | null;
  status: ReportBatchStatus;
  totalCount: number;
  succeededCount: number;
  failedCount: number;
  skippedCount: number;
  pendingCount: number;
  processingCount: number;
  finishedCount: number;
  createdAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface ReportBatchItem {
  id: number;
  userId: number;
  studentName: string;
  status: ReportBatchItemStatus;
  attempts: number;
  maxAttempts: number;
  manualRetryCount: number;
  errorCode?: string | null;
  errorMessage?: string | null;
  skipReason?: string | null;
  reportId?: number | null;
  finishedAt?: string | null;
}

export interface ReportBatchItemReport {
  batchId: number;
  itemId: number;
  eventId: number;
  studentName: string;
  content: string;
  evidenceJson?: string | null;
  generatedAt?: string | null;
}

export const createReportBatchApi = async (eventId: number, idempotencyKey: string): Promise<ReportBatch> => {
  const { data } = await request.post<ApiResponse<ReportBatch>>('/teacher/report-batches', { eventId, idempotencyKey });
  return data.data;
};

export const listReportBatchesApi = async (page = 1, size = 20, eventId?: number): Promise<PageResult<ReportBatch>> => {
  const { data } = await request.get<ApiResponse<PageResult<ReportBatch>>>('/teacher/report-batches', { params: { page, size, eventId } });
  return data.data;
};

export const getReportBatchApi = async (batchId: number): Promise<ReportBatch> => {
  const { data } = await request.get<ApiResponse<ReportBatch>>(`/teacher/report-batches/${batchId}`);
  return data.data;
};

export const listReportBatchItemsApi = async (batchId: number, page = 1, size = 100): Promise<PageResult<ReportBatchItem>> => {
  const { data } = await request.get<ApiResponse<PageResult<ReportBatchItem>>>(`/teacher/report-batches/${batchId}/items`, { params: { page, size } });
  return data.data;
};

export const retryReportBatchItemApi = async (batchId: number, itemId: number): Promise<ReportBatchItem> => {
  const { data } = await request.post<ApiResponse<ReportBatchItem>>(`/teacher/report-batches/${batchId}/items/${itemId}/retry`);
  return data.data;
};

export const getReportBatchItemReportApi = async (batchId: number, itemId: number): Promise<ReportBatchItemReport> => {
  const { data } = await request.get<ApiResponse<ReportBatchItemReport>>(`/teacher/report-batches/${batchId}/items/${itemId}/report`);
  return data.data;
};

export const isReportBatchActive = (batch?: ReportBatch | null) =>
  batch?.status === 'PENDING' || batch?.status === 'PROCESSING';

export const reportBatchStatusText = (status?: string | null) => {
  switch (status) {
    case 'PENDING': return '排队中';
    case 'PROCESSING': return '生成中';
    case 'SUCCEEDED': return '已完成';
    case 'PARTIAL_FAILED': return '部分失败';
    case 'FAILED': return '全部失败';
    default: return status ?? '';
  }
};

export const reportBatchItemStatusText = (status?: string | null) => {
  switch (status) {
    case 'PENDING': return '排队中';
    case 'PROCESSING': return '生成中';
    case 'SUCCEEDED': return '已生成';
    case 'FAILED': return '生成失败';
    case 'SKIPPED': return '无作答跳过';
    default: return status ?? '';
  }
};
