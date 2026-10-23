package com.zhixuewuli.repository;

import com.zhixuewuli.entity.AssistantConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {

    List<AssistantConversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Optional<AssistantConversation> findFirstByUserIdAndModeAndEventIdAndRecordIdOrderByUpdatedAtDesc(
            Long userId, String mode, Long eventId, Long recordId);

    Optional<AssistantConversation> findByIdAndUserId(Long id, Long userId);
}
