package com.zhixuewuli.repository;

import com.zhixuewuli.entity.QuestionIndexPublication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionIndexPublicationRepository extends JpaRepository<QuestionIndexPublication, Long> {

    Optional<QuestionIndexPublication> findByQuestionIdAndRevision(Long questionId, Long revision);

    void deleteByQuestionId(Long questionId);
}
