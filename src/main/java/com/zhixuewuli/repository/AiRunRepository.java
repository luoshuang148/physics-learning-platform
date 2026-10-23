package com.zhixuewuli.repository;
import com.zhixuewuli.entity.AiRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AiRunRepository extends JpaRepository<AiRun,Long> { Optional<AiRun> findByConversationHistoryId(Long historyId); }
