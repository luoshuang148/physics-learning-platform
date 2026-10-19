package com.zhixuewuli.repository;

import com.zhixuewuli.entity.TeacherEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherEventRepository extends JpaRepository<TeacherEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TeacherEvent e where e.id=:id")
    Optional<TeacherEvent> lockById(@Param("id") Long id);

    /** Multiple first attempts may read the task concurrently; close/archive waits for them. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from TeacherEvent e where e.id=:id")
    Optional<TeacherEvent> lockSharedById(@Param("id") Long id);

    /** Legacy single-task compatibility: newest published task. */
    @Query(value = "select * from teacher_events where status='PUBLISHED' order by created_at desc, id desc limit 1", nativeQuery = true)
    Optional<TeacherEvent> findByIsGlobalTrue();

    @Query("select count(e) > 0 from TeacherEvent e where e.status = com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED")
    boolean existsByIsGlobalTrue();

    /** Legacy history endpoint: archived tasks. */
    @Query("select e from TeacherEvent e where e.status = com.zhixuewuli.entity.TeacherEvent.Status.ARCHIVED")
    Page<TeacherEvent> findByIsGlobalFalse(Pageable page);

    Page<TeacherEvent> findByStatusAndClassIdIn(TeacherEvent.Status status, java.util.Collection<Long> classIds, Pageable page);

    List<TeacherEvent> findByStatusNotOrderByCreatedAtDesc(TeacherEvent.Status status);

    List<TeacherEvent> findByStatusOrderByCreatedAtDesc(TeacherEvent.Status status);

    List<TeacherEvent> findByStatusOrderByStartAtAscCreatedAtDesc(TeacherEvent.Status status);

    @Query("select e from TeacherEvent e where e.status = com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED order by e.createdAt desc, e.id desc")
    List<TeacherEvent> findPublishedTasks();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select e from TeacherEvent e where e.status=com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED order by e.id")
    List<TeacherEvent> findPublishedTasksForRoster();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select e from TeacherEvent e where e.status=com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED and e.classId=:classId order by e.id")
    List<TeacherEvent> findPublishedTasksForRosterByClass(@Param("classId") Long classId);

    @Query("select e from TeacherEvent e where e.status in (com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED, com.zhixuewuli.entity.TeacherEvent.Status.CLOSED) " +
            "or (e.status=com.zhixuewuli.entity.TeacherEvent.Status.ARCHIVED " +
            "and exists(select a.id from AnswerRecord a where a.eventId=e.id and a.userId=:userId and a.practice=false)) " +
            "order by e.createdAt desc, e.id desc")
    List<TeacherEvent> findStudentReviewTasks(@Param("userId") Long userId);

    long countByStatus(TeacherEvent.Status status);

    long countByStatusNot(TeacherEvent.Status status);

    /** Legacy helper kept for older tests/callers; prefer lists in new code. */
    Optional<TeacherEvent> findByTeacherId(Long teacherId);

    boolean existsByTeacherId(Long teacherId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TeacherEvent te WHERE te.teacherId = :teacherId")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TeacherEvent")
    int deleteAllEvents();
}
