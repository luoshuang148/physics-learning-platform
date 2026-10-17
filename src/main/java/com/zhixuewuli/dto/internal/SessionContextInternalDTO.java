package com.zhixuewuli.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.zhixuewuli.entity.ConversationHistory;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionContextInternalDTO {

    private String sessionId;
    private Long userId;
    private ConversationType conversationType;
    private Long questionId;
    private String context;
    private Integer maxTokens;
    private AtomicInteger usedTokens;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveTime;
    private Boolean isActive;
    private volatile boolean cancelled;
    private Integer maxInteractionCount;
    private ConcurrentSkipListMap<Integer, ConversationHistory> interactionRecords;
    private Integer totalInteractionCount;

    // 读写锁，transient 防止序列化
    @Builder.Default
    private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void initializeInteractionRecords() {
        if (this.interactionRecords == null) {
            this.interactionRecords = new ConcurrentSkipListMap<>();
        }
    }

    /**
     * 生成下一个可用的序列号（在写锁内调用）
     */
    public int generateNextSequence() {
        lock.writeLock().lock();
        try {
            int next = (totalInteractionCount == null ? 0 : totalInteractionCount) + 1;
            totalInteractionCount = next;
            return next;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 添加交互记录，不负责生成序列号，只负责插入和裁剪
     */
    public void addInteractionRecord(ConversationHistory record) {
        lock.writeLock().lock();
        try {
            initializeInteractionRecords();
            int seq = record.getSequenceNumber(); // 序列号已由调用者设置
            interactionRecords.put(seq, record);

            // 如果超过最大交互数，移除最早的
            if (maxInteractionCount != null && maxInteractionCount > 0
                    && interactionRecords.size() > maxInteractionCount) {
                Integer oldestKey = interactionRecords.firstKey();
                interactionRecords.remove(oldestKey);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ConcurrentSkipListMap<Integer, ConversationHistory> getInteractionRecords() {
        lock.readLock().lock();
        try {
            return interactionRecords;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Integer getTotalInteractionCount() {
        lock.readLock().lock();
        try {
            return totalInteractionCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    // usedTokens 的原子操作保持不变
    public Integer getUsedTokens() {
        return usedTokens.get();
    }

    public void setUsedTokens(Integer usedTokens) {
        this.usedTokens.set(usedTokens);
    }

    public void setUsedTokens(AtomicInteger usedTokens) {
        this.usedTokens = usedTokens;
    }

    public int addAndGetUsedTokens(int delta) {
        return usedTokens.addAndGet(delta);
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }
}