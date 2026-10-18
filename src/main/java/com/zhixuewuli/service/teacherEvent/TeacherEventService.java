package com.zhixuewuli.service.teacherEvent;

import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.dto.response.TeacherEventResponse;

import java.util.List;

public interface TeacherEventService {
    TeacherEventResponse replaceEvent(Long expectedId, TeacherEventCreateRequest request, Long userId);

    void archiveEvent(Long expectedId, Long userId);

    TeacherEventResponse getEvent(Long id);

    TeacherEventResponse getEvent(Long id, Long viewerId);

    List<QuestionResponse> getEventQuestions(Long id, Long viewerId);

    com.zhixuewuli.common.result.PageResult<TeacherEventResponse> getEventHistory(Long userId, int page, int size);

    /** Legacy endpoint: create a task. status defaults to PUBLISHED. */
    TeacherEventResponse publishEvent(TeacherEventCreateRequest request, Long currentUserId);

    void deleteCurrentEvent(Long currentUserId);

    /** Legacy single-task endpoint: newest published task or null. */
    TeacherEventResponse getGlobalEvent();

    List<TeacherEventResponse> listTeacherTasks(Long userId);

    List<TeacherEventResponse> listStudentTasks(Long userId);

    TeacherEventResponse updateDraft(Long id, TeacherEventCreateRequest request, Long userId);

    TeacherEventResponse publishTask(Long id, Long userId);

    TeacherEventResponse closeTask(Long id, Long userId);
}
