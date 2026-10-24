import request from '@/utils/request';
import type { ApiResponse, PageResult } from '@/types/common';
import type {
  AnswerRecordBatchResponse,
  AnswerRecordResponse,
  AnswerRecordSubmitPayload,
  AnswerStatsResponse,
  AnswerRecordSummary,
  StudentAnswerSummary,
} from '@/types/answer-record';

export const submitAnswerApi = async (
  payload: AnswerRecordSubmitPayload,
): Promise<AnswerRecordResponse> => {
  const { data } = await request.post<ApiResponse<AnswerRecordResponse>>(
    '/answer-records/student/submit',
    payload,
  );
  return data.data;
};

export const getMyAnswersApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    '/answer-records/student/my',
  );
  return data.data;
};

export const getMyWrongAnswersApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    '/answer-records/student/my/wrong',
  );
  return data.data;
};

export const getAllAnswerRecordsApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    '/answer-records/teacher/all',
  );
  return data.data;
};

export const getAllWrongAnswerRecordsApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    '/answer-records/teacher/wrong/all',
  );
  return data.data;
};

export const getUserAnswerRecordsApi = async (
  userId: number,
): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    `/answer-records/user/${userId}`,
  );
  return data.data;
};

export const getUserWrongAnswerRecordsApi = async (
  userId: number,
): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    `/answer-records/user/wrong/${userId}`,
  );
  return data.data;
};

export const getSessionAnswerRecordsApi = async (
  sessionId: string,
): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    `/answer-records/session/${encodeURIComponent(sessionId)}`,
  );
  return data.data;
};

export const getStudentLatestSessionRecordsApi = async (
  studentId: number,
): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    `/answer-records/teacher/${studentId}/latest-session`,
  );
  return data.data;
};

export const getTotalAnswerCountApi = async (): Promise<number> => {
  const { data } = await request.get<ApiResponse<number>>('/answer-records/teacher/count');
  return data.data;
};

export const getAnswerStatsApi = async (): Promise<AnswerStatsResponse> => {
  const { data } = await request.get<ApiResponse<AnswerStatsResponse>>(
    '/answer-records/teacher/stats',
  );
  return data.data;
};

export const getMyAnswerPageApi = async (params: {
  page: number;
  size: number;
  wrongOnly?: boolean;
  keyword?: string;
}): Promise<PageResult<AnswerRecordSummary>> => {
  const { data } = await request.get<ApiResponse<PageResult<AnswerRecordSummary>>>(
    '/answer-records/student/history',
    { params },
  );
  return data.data;
};

export const getStudentAnswerSummaryApi = async (): Promise<StudentAnswerSummary> => {
  const { data } = await request.get<ApiResponse<StudentAnswerSummary>>(
    '/answer-records/student/summary',
  );
  return data.data;
};

export const getPracticeReviewPageApi = async (params: {
  page: number;
  size: number;
  keyword?: string;
  mastered?: boolean;
}): Promise<PageResult<AnswerRecordSummary>> => {
  const { data } = await request.get<ApiResponse<PageResult<AnswerRecordSummary>>>(
    '/answer-records/student/practice-review/page',
    { params },
  );
  return data.data;
};

export const getUserAnswerPageApi = async (
  userId: number,
  params: { page: number; size: number; wrongOnly?: boolean; keyword?: string },
): Promise<PageResult<AnswerRecordSummary>> => {
  const { data } = await request.get<ApiResponse<PageResult<AnswerRecordSummary>>>(
    '/answer-records/user/' + userId + '/history',
    { params },
  );
  return data.data;
};

export const getAnswerRecordDetailApi = async (recordId: string): Promise<AnswerRecordResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordResponse>>(
    '/answer-records/' + encodeURIComponent(recordId),
  );
  return data.data;
};

export const getTaskAnswersApi = async (eventId: number): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    `/answer-records/student/task/${eventId}`,
  );
  return data.data;
};
export const getPracticeReviewApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(
    '/answer-records/student/practice-review',
  );
  return data.data;
};

export interface LearningSummary {
  eventId: number | null;
  totalAttempts: number;
  correctAttempts: number;
  assignmentAnswers: number;
  assignmentCorrect: number;
  participatingStudents: number;
  completedStudents: number;
  eligibleStudents: number;
  questionCount: number;
  practiceAttempts: number;
  masteredQuestions: number;
  generatedAt: string;
}
export const getLearningSummaryApi = async (eventId?: number): Promise<LearningSummary> => {
  const { data } = await request.get<ApiResponse<LearningSummary>>(
    '/answer-records/teacher/summary',
    { params: { eventId } },
  );
  return data.data;
};

export interface StudentAnswerStats {
  userId: number;
  totalCount: number;
  wrongCount: number;
}

export const getStudentAnswerStatsApi = async (): Promise<StudentAnswerStats[]> => {
  const { data } = await request.get<ApiResponse<StudentAnswerStats[]>>(
    '/answer-records/teacher/student-stats',
  );
  return data.data;
};
