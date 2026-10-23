package com.zhixuewuli.repository;
import com.zhixuewuli.entity.AiMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AiMessageSourceRepository extends JpaRepository<AiMessageSource,Long> { List<AiMessageSource> findByRunIdOrderById(Long runId); }
