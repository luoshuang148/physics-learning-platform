package com.zhixuewuli.service.teacherEvent;

import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.dto.response.TeacherEventResponse;

import java.util.List;

public interface TeacherEventService {
    TeacherEventResponse replaceEvent(Long expectedId, TeacherEventCreateRequest request, Long userId);

    void archiveEvent(Long expectedId);

    TeacherEventResponse getEvent(Long id);

    TeacherEventResponse getEvent(Long id, Long viewerId);

    List<QuestionResponse> getEventQuestions(Long id, Long viewerId);

    com.zhixuewuli.common.result.PageResult<TeacherEventResponse> getEventHistory(int page, int size);

    /** Legacy endpoint: create a task. status defaults to PUBLISHED. */
    TeacherEventResponse publishEvent(TeacherEventCreateRequest request, Long currentUserId);

    void deleteCurrentEvent();

    /** Legacy single-task endpoint: newest published task or null. */
    TeacherEventResponse getGlobalEvent();

    List<TeacherEventResponse> listTeacherTasks();

    List<TeacherEventResponse> listStudentTasks(Long userId);

    List<com.zhixuewuli.dto.response.StudentTaskResultResponse> listStudentTaskResults(Long userId);

    com.zhixuewuli.dto.response.StudentTaskResultResponse getStudentTaskResult(Long id, Long userId);

    com.zhixuewuli.dto.response.TaskDetailResponse getTaskDetail(Long id, Long userId);

    java.util.List<com.zhixuewuli.dto.response.TaskOverviewResponse> getTaskOverview();

    TeacherEventResponse updateDraft(Long id, TeacherEventCreateRequest request);

    TeacherEventResponse publishTask(Long id);

    TeacherEventResponse closeTask(Long id);
}
