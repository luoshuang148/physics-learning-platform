package com.zhixuewuli.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskOverviewResponse {
    private Long taskId;
    private String title;
    private String status;
    private String availability;
    private String startAt;
    private String dueAt;
    private int questionCount;
    private long totalStudents;
    private long participatedStudents;
    private long completedStudents;
    private long notStartedStudents;
    private double completionRate;
    private double accuracy;
    private long answeredQuestions;
    private long correctQuestions;
}
