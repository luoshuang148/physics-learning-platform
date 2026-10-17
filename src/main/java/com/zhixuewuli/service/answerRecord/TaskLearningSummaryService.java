package com.zhixuewuli.service.answerRecord;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.response.LearningSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class TaskLearningSummaryService {
 private final AnswerRecordRepository answers;
 private final UserRepository users;
 private final QuestionRepository questions;
 private final TeacherEventQuestionRepository taskQuestions;
 private final TeacherEventRepository tasks;
 private final UserPermissionUtil permissions;
 @Transactional(readOnly=true)
 public LearningSummary summary(Long operator,Long eventId) {
  permissions.checkTeacherPermission(operator);
  if(eventId!=null && !tasks.existsById(eventId)) throw new IllegalArgumentException("任务不存在");
  var totals=answers.summarize(eventId);
  long questionCount=eventId==null?questions.count():taskQuestions.countByEventId(eventId);
  long eligible=users.countByUserTypeAndActiveTrue(com.zhixuewuli.entity.User.UserType.STUDENT);
  return new LearningSummary(eventId,totals.getTotalAttempts(),totals.getCorrectAttempts(),totals.getAssignmentAnswers(),
   totals.getAssignmentCorrect(),totals.getParticipatingStudents(),eventId==null||questionCount==0?0:answers.countCompletedStudents(eventId,questionCount),
   eligible,questionCount,totals.getPracticeAttempts(),answers.countMasteredQuestions(eventId),java.time.Instant.now());
 }
}
