package com.zhixuewuli.repository;

import com.zhixuewuli.entity.QuestionIndexJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionIndexJobRepository extends JpaRepository<QuestionIndexJob, Long> {

    Optional<QuestionIndexJob> findByQuestionIdAndRevisionAndJobType(Long questionId, Long revision, String jobType);

    @Query("""
            select j from QuestionIndexJob j
            where (j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.PENDING
                   or (j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.INDEXING
                       and j.leaseExpiresAt < :now))
              and j.attempts < :maxAttempts
            order by j.updatedAt asc, j.id asc
            """)
    List<QuestionIndexJob> findRunnable(@Param("now") LocalDateTime now,
                                        @Param("maxAttempts") int maxAttempts,
                                        Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuestionIndexJob j set j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.INDEXING,
                j.leaseOwner = :owner, j.leaseExpiresAt = :leaseUntil, j.attempts = j.attempts + 1
            where j.id = :id
              and (j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.PENDING
                   or (j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.INDEXING
                       and j.leaseExpiresAt < :now))
              and j.attempts < :maxAttempts
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now,
              @Param("maxAttempts") int maxAttempts);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuestionIndexJob j set j.leaseOwner = :owner, j.leaseExpiresAt = :leaseUntil
            where j.id = :id and j.status = com.zhixuewuli.entity.QuestionIndexJob.Status.INDEXING
              and j.leaseOwner = :owner
            """)
    int renewLease(@Param("id") Long id, @Param("owner") String owner,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    List<QuestionIndexJob> findByQuestionIdOrderByUpdatedAtDesc(Long questionId);
}
