package com.zhixuewuli.dto.response;
import java.time.Instant;
public record LearningSummary(Long eventId,long totalAttempts,long correctAttempts,long assignmentAnswers,
 long assignmentCorrect,long participatingStudents,long completedStudents,long eligibleStudents,long questionCount,
 long practiceAttempts,long masteredQuestions,Instant generatedAt) {}
