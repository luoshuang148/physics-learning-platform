package com.zhixuewuli.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskDetailResponse {
    private Long taskId;
    private String title;
    private String status;
    private String availability;
    private String startAt;
    private String dueAt;
    private Integer questionCount;
    private long totalStudents;
    private long participatedStudents;
    private long completedStudents;
    private long notStartedStudents;
    private long partialStudents;
    private double completionRate;
    private double participationRate;
    private double accuracy;
    private long answeredQuestions;
    private long correctQuestions;
    private List<StudentRow> students;
    private List<QuestionRow> questions;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentRow {
        private Long userId;
        private String username;
        private String realName;
        private String className;
        private String status;
        private long answeredQuestions;
        private long correctQuestions;
        private long missingQuestions;
        private String lastAnsweredAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuestionRow {
        private Long questionId;
        private String contentPreview;
        private long answeredStudents;
        private long correctStudents;
        private long unansweredStudents;
        private double accuracy;
    }
}
