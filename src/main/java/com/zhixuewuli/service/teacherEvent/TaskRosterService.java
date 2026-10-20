package com.zhixuewuli.service.teacherEvent;

import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.classroom.ClassAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class TaskRosterService {
    private final TaskRecipientRepository recipients;
    private final UserRepository users;
    private final TeacherEventRepository events;
    private final ClassStudentRepository classStudents;
    private final ClassAccessService classAccess;

    /** Caller holds ClassroomGuard; locking reads also see concurrent commits under MySQL repeatable-read. */
    public void capture(Long eventId, Long classId) {
        for (User user : classStudents.findActiveStudents(classId)) add(eventId,user.getId(),"PUBLISHED");
    }

    /** Legacy fallback for callers created before class scoping. */
    public void capture(Long eventId) {
        for (User user : users.findActiveStudentsForRoster()) add(eventId,user.getId(),"PUBLISHED");
    }

    public void enroll(User user) {
        if (user.getUserType()!=User.UserType.STUDENT || !Boolean.TRUE.equals(user.getActive())) return;
        var membership=classStudents.findByUserId(user.getId());
        if(membership.isEmpty()) {
            var defaultClass=classAccess.defaultClass();
            classAccess.ensureStudent(defaultClass.getId(),user.getId());
        }
        Long classId=classStudents.findByUserId(user.getId()).map(ClassStudent::getClassId).orElse(null);
        if(classId==null) return;
        var now=LocalDateTime.now(ZoneOffset.UTC);
        for (TeacherEvent event : events.findPublishedTasksForRosterByClass(classId)) {
            if (!event.isEndedAt(now)) add(event.getId(),user.getId(),"JOINED");
        }
    }
    /** Submission already owns the user row and a shared event lock. */
    public void ensureAnswerer(Long eventId,Long userId) { add(eventId,userId,"ANSWERED"); }
    private void add(Long eventId,Long userId,String source) {
        if (recipients.findByEventIdAndUserId(eventId,userId).isPresent()) return;
        var row=new TaskRecipient();row.setEventId(eventId);row.setUserId(userId);row.setSource(source);recipients.save(row);
    }
}
