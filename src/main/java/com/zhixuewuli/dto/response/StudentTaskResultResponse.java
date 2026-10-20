package com.zhixuewuli.dto.response;

/** Own first-attempt results; revision also changes after supplementary practice. */
public record StudentTaskResultResponse(TeacherEventResponse task, long answeredQuestions,
        long correctQuestions, String answerRevision, SavedReport savedReport) {
    public StudentTaskResultResponse(TeacherEventResponse task,long answeredQuestions,long correctQuestions,String answerRevision) {
        this(task,answeredQuestions,correctQuestions,answerRevision,null);
    }
    public record SavedReport(String content,String answerRevision,String generatedAt) {}
}
