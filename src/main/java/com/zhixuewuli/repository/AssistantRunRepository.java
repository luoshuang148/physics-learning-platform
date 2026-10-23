package com.zhixuewuli.repository;

import com.zhixuewuli.entity.AssistantRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistantRunRepository extends JpaRepository<AssistantRun, Long> {

    Optional<AssistantRun> findByRunId(String runId);

    Optional<AssistantRun> findByUserIdAndRequestId(Long userId, String requestId);
}
