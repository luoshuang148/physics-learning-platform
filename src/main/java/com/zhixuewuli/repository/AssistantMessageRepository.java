package com.zhixuewuli.repository;

import com.zhixuewuli.entity.AssistantMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    List<AssistantMessage> findByConversationIdOrderBySeqAsc(Long conversationId, Pageable pageable);

    List<AssistantMessage> findByConversationIdOrderBySeqDesc(Long conversationId, Pageable pageable);

    int countByConversationId(Long conversationId);
}
