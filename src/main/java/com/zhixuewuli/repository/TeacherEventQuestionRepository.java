package com.zhixuewuli.repository;
import com.zhixuewuli.entity.TeacherEventQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TeacherEventQuestionRepository extends JpaRepository<TeacherEventQuestion,Long> {
 List<TeacherEventQuestion> findByEventIdOrderByPositionNumberAsc(Long eventId);
 Optional<TeacherEventQuestion> findByEventIdAndQuestionId(Long eventId,Long questionId);
}
