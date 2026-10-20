package com.zhixuewuli.repository;

import com.zhixuewuli.entity.TaskRecipient;
import com.zhixuewuli.entity.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TaskRecipientRepository extends JpaRepository<TaskRecipient,Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskRecipient> findByEventIdAndUserId(Long eventId,Long userId);
    boolean existsByUserId(Long userId);
    long countByEventId(Long eventId);
    boolean existsByEventIdAndSource(Long eventId,String source);
    @Query("select u from User u join TaskRecipient r on r.userId=u.id where r.eventId=:eventId order by u.id")
    List<User> students(@Param("eventId") Long eventId);
    interface CountRow { Long getEventId(); Long getTotal(); }
    @Query("select r.eventId as eventId,count(r) as total from TaskRecipient r where r.eventId in :ids group by r.eventId")
    List<CountRow> counts(@Param("ids") Collection<Long> ids);
    @Query("select r.eventId from TaskRecipient r where r.userId=:userId")
    Set<Long> taskIds(@Param("userId") Long userId);
}
