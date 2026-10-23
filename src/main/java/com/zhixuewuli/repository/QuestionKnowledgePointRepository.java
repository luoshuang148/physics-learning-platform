package com.zhixuewuli.repository;

import com.zhixuewuli.entity.QuestionKnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface QuestionKnowledgePointRepository extends JpaRepository<QuestionKnowledgePoint, Long> {
    @Query("select distinct q.question.id from QuestionKnowledgePoint q where q.knowledgePointCode in :codes order by q.question.id desc")
    java.util.List<Long> findQuestionIdsByKnowledgePointCodes(@Param("codes") Collection<String> codes,
                                                              org.springframework.data.domain.Pageable pageable);
}
