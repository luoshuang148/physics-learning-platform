package com.zhixuewuli.repository;
import com.zhixuewuli.entity.AiRunStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AiRunStepRepository extends JpaRepository<AiRunStep,Long> { List<AiRunStep> findByRunIdOrderByStepOrder(Long runId); }
