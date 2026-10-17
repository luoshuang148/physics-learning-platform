package com.zhixuewuli.repository;
import com.zhixuewuli.entity.TeacherEventQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TeacherEventQuestionRepository extends JpaRepository<TeacherEventQuestion,Long> {
 boolean existsByQuestionId(Long questionId);
 long countByEventId(Long eventId);
 @org.springframework.data.jpa.repository.Query("select q.questionId from TeacherEventQuestion q where q.eventId=:id order by q.positionNumber")
 List<Long> findQuestionIdsByEventId(@org.springframework.data.repository.query.Param("id") Long id);
 List<TeacherEventQuestion> findByEventIdOrderByPositionNumberAsc(Long eventId);
 Optional<TeacherEventQuestion> findByEventIdAndQuestionId(Long eventId,Long questionId);
}
