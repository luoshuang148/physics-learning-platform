package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.ai.ZhiPuAIAssistantService;
import com.zhixuewuli.service.session.SessionManager;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class SessionPersistenceRegressionTest extends BaseControllerIntegrationTest {
    @Autowired ZhiPuAIAssistantService ai;
    @Autowired SessionManager manager;
    @Autowired ConversationHistoryRepository histories;
    @Autowired AnswerRecordRepository answers;
    @Autowired AIBusinessProperties properties;

    @Test
    void historyAndClearAreRestrictedToTheAuthenticatedOwner() {
        Long alice = user("alice");
        Long bob = user("bob");
        history(alice, "alice-private", 1, 12);
        history(bob, "bob-private", 1, 20);
        assertThat(ai.getSessionHistory("shared-id", alice)).extracting(ConversationHistory::getAnswer)
                .containsExactly("alice-private");
        manager.clearSession("shared-id", alice);
        assertThat(histories.findAll()).extracting(ConversationHistory::getAnswer).containsExactly("bob-private");
    }

    @Test
    void aNewManagerRestoresSequenceHistoryAndTokenUsage() {
        Long alice = user("recover_alice");
        history(alice, "previous-response", 7, 90);
        var restarted = new SessionManager(histories, answers, properties);
        var recovered = restarted.getOrCreateSession("shared-id", alice, ConversationType.REAL_TIME_QA,
                null, () -> "physics");
        assertThat(recovered.getInteractionRecords().values()).extracting(ConversationHistory::getAnswer)
                .containsExactly("previous-response");
        assertThat(recovered.getUsedTokens()).isEqualTo(90);
        assertThat(recovered.generateNextSequence()).isEqualTo(8);
    }

    private Long user(String name) {
        User user = new User(); user.setUsername(name); user.setRealName(name);
        user.setPasswordHash("test-hash"); user.setActive(true); user.setUserType(User.UserType.STUDENT);
        jpaEntityManager.persist(user); jpaEntityManager.flush(); return user.getId();
    }
    private void history(Long userId, String answer, int sequence, int tokens) {
        ConversationHistory row = new ConversationHistory(); row.setUserId(userId); row.setSessionId("shared-id");
        row.setConversationType(ConversationType.REAL_TIME_QA); row.setQuestion("question"); row.setAnswer(answer);
        row.setSequenceNumber(sequence); row.setTokenCount(tokens); row.setIsActive(true);
        histories.saveAndFlush(row);
    }
}
