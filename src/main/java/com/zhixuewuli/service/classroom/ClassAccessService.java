package com.zhixuewuli.service.classroom;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.entity.ClassStudent;
import com.zhixuewuli.entity.ClassTeacher;
import com.zhixuewuli.entity.TeachingClass;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.ClassStudentRepository;
import com.zhixuewuli.repository.ClassTeacherRepository;
import com.zhixuewuli.repository.TeachingClassRepository;
import com.zhixuewuli.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Class membership and authorization. Teachers may belong to many classes; students have one primary class. */
@Service
@RequiredArgsConstructor
@Transactional
public class ClassAccessService {
    private final TeachingClassRepository classes;
    private final ClassStudentRepository students;
    private final ClassTeacherRepository teachers;
    private final UserRepository users;

    public TeachingClass requireClass(Long classId) {
        return classes.findById(classId).orElseThrow(() -> new BusinessException("班级不存在"));
    }

    public ClassTeacher requireTeacher(Long classId, Long teacherId) {
        if (classId == null || teacherId == null) throw new BusinessException("无权访问该班级");
        return teachers.findByClassIdAndTeacherId(classId, teacherId)
                .orElseThrow(() -> new BusinessException("无权访问该班级"));
    }

    public void requireStudent(Long classId, Long studentId) {
        var membership = students.findByUserId(studentId)
                .orElseThrow(() -> new BusinessException("学生尚未加入班级"));
        if (!membership.getClassId().equals(classId)) throw new BusinessException("无权访问该班级任务");
    }

    public List<TeachingClass> classesForTeacher(Long teacherId) {
        return teachers.findActiveClassesForTeacher(teacherId);
    }

    public TeachingClass defaultClass() {
        return classes.findByName("默认班级").orElseGet(() -> {
            var created = new TeachingClass();
            created.setName("默认班级");
            created.setStatus(TeachingClass.Status.ACTIVE);
            return classes.saveAndFlush(created);
        });
    }

    /** Legacy callers without classId resolve to the teacher's first class, or the default class. */
    public TeachingClass resolveForTask(Long classId, Long teacherId) {
        TeachingClass target;
        if (classId != null) {
            target = requireClass(classId);
            requireTeacher(classId, teacherId);
            return target;
        }
        var owned = classesForTeacher(teacherId);
        target = owned.isEmpty() ? defaultClass() : owned.getFirst();
        if (!teachers.existsByClassIdAndTeacherId(target.getId(), teacherId)) {
            addTeacher(target.getId(), teacherId, ClassTeacher.Role.OWNER);
        }
        ensureClassDefaults(target.getId());
        return target;
    }

    public TeachingClass resolveForStudent(Long studentId) {
        var membership = students.findByUserId(studentId);
        if (membership.isPresent()) return requireClass(membership.get().getClassId());
        var target = defaultClass();
        ensureStudent(target.getId(), studentId);
        ensureClassDefaults(target.getId());
        return target;
    }

    public void ensureStudent(Long classId, Long studentId) {
        if (students.existsByClassIdAndUserId(classId, studentId)) return;
        var existing = students.findByUserId(studentId);
        if (existing.isPresent()) {
            if (existing.get().getClassId().equals(classId)) return;
            throw new BusinessException("学生已加入其他班级");
        }
        var row = new ClassStudent();
        row.setClassId(classId); row.setUserId(studentId); row.setJoinedAt(LocalDateTime.now());
        students.save(row);
    }

    public void addTeacher(Long classId, Long teacherId, ClassTeacher.Role role) {
        if (teachers.existsByClassIdAndTeacherId(classId, teacherId)) return;
        var row = new ClassTeacher();
        row.setClassId(classId); row.setTeacherId(teacherId); row.setRole(role);
        teachers.save(row);
    }

    /** H2 and legacy callers may start with empty class tables; preserve the old shared-class behavior. */
    private void ensureClassDefaults(Long classId) {
        for (User student : users.findActiveStudentsForRoster()) {
            if (students.findByUserId(student.getId()).isEmpty()) ensureStudent(classId, student.getId());
        }
        for (User teacher : users.findByUserTypeAndActiveTrue(User.UserType.TEACHER)) {
            if (teachers.findByClassIdAndTeacherId(classId, teacher.getId()).isEmpty()) {
                addTeacher(classId, teacher.getId(), ClassTeacher.Role.CO_TEACHER);
            }
        }
    }
}
