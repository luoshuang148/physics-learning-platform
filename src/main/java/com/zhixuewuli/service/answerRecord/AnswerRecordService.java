package com.zhixuewuli.service.answerRecord;

import java.util.List;
import com.zhixuewuli.dto.response.StudentAnswerStats;
import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.dto.response.AnswerRecordBatchResponse;
import com.zhixuewuli.dto.response.AnswerStatsResponse;
import com.zhixuewuli.dto.response.AnswerRecordSummaryResponse;
import com.zhixuewuli.dto.response.StudentAnswerSummary;

public interface AnswerRecordService {
    List<StudentAnswerStats> getStudentAnswerStats();

    PageResult<AnswerRecordResponse> getTeacherAnswerPage(int page,int size,boolean wrongOnly,String studentName,Long eventId);
    PageResult<AnswerRecordResponse> getTeacherAnswerPage(int page, int size, boolean wrongOnly, String studentName);

    // === 学生基础功能 ===

    AnswerRecordResponse submitAnswerRecord(AnswerRecordSubmitRequest request, Long currentUserId, String sessionId);

    AnswerRecordResponse getAnswerRecordById(String recordId, Long currentUserId);

    PageResult<AnswerRecordSummaryResponse> getStudentHistory(Long currentUserId, int page, int size, boolean wrongOnly, String keyword);

    PageResult<AnswerRecordSummaryResponse> getPracticeReviewPage(Long currentUserId, int page, int size, String keyword);

    PageResult<AnswerRecordSummaryResponse> getUserHistory(Long targetUserId, Long currentUserId, int page, int size, boolean wrongOnly, String keyword);

    StudentAnswerSummary getStudentAnswerSummary(Long currentUserId);

    AnswerRecordBatchResponse getCurrentUserWrongAnswers(Long currentUserId);

    AnswerRecordBatchResponse getCurrentUserAnswerRecords(Long currentUserId);

    // === 权限控制查询 ===

    AnswerRecordBatchResponse getUserAnswerRecords(Long targetUserId, Long currentUserId);

    AnswerRecordBatchResponse getWrongAnswersByUser(Long targetUserId, Long currentUserId);

    // === 教师专属功能 ===

    void deleteAnswerRecord(String recordId, Long currentUserId);

    void batchDeleteByTimeRange(RecordBatchDeleteRequest request, Long currentUserId);

    Long countTotalAnswers(Long currentUserId);

    AnswerRecordBatchResponse getAllUsersAnswerRecords(Long currentUserId);

    AnswerRecordBatchResponse getAllUsersWrongAnswers(Long currentUserId);

    // === 会话维度查询功能 ===

    AnswerRecordBatchResponse getSessionAnswerRecords(String sessionId, Long currentUserId);

    AnswerRecordBatchResponse getStudentLatestSessionRecords(Long studentId, Long teacherId);

    AnswerRecordBatchResponse getStudentLatestSessionRecordsByName(String studentName, Long teacherId);

    AnswerStatsResponse getAnswerStats(Long currentUserId);
}
