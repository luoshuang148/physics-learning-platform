package com.zhixuewuli.service.classroom;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.dto.request.ClassCreateRequest;
import com.zhixuewuli.dto.response.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassroomService {
    private final TeachingClassRepository classes;
    private final ClassStudentRepository students;
    private final ClassTeacherRepository teachers;
    private final UserRepository users;
    private final ClassAccessService access;

    public List<TeachingClassResponse> listForTeacher(Long teacherId) {
        List<TeachingClassResponse> result = new ArrayList<>();
        for (TeachingClass c : access.classesForTeacher(teacherId)) {
            var membership = teachers.findByClassIdAndTeacherId(c.getId(), teacherId).orElse(null);
            result.add(toResponse(c, membership == null ? null : membership.getRole().name()));
        }
        return result;
    }

    public TeachingClassResponse create(ClassCreateRequest request, Long teacherId) {
        if (classes.findByName(request.getName().trim()).isPresent())
            throw new BusinessException("班级名称已存在");
        TeachingClass c = new TeachingClass();
        c.setName(request.getName().trim());
        c.setGrade(request.getGrade());
        c = classes.saveAndFlush(c);
        access.addTeacher(c.getId(), teacherId, ClassTeacher.Role.OWNER);
        return toResponse(c, ClassTeacher.Role.OWNER.name());
    }

    public List<ClassStudentRow> listStudents(Long classId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        return students.findByClassId(classId).stream()
                .map(row -> users.findById(row.getUserId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(u -> ClassStudentRow.builder().userId(u.getId()).username(u.getUsername())
                        .realName(u.getRealName()).className(u.getClassName()).active(Boolean.TRUE.equals(u.getActive())).build())
                .sorted(Comparator.comparing(ClassStudentRow::getRealName))
                .toList();
    }

    public void addStudent(Long classId, Long studentId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        User user = users.findById(studentId).orElseThrow(() -> new BusinessException("学生不存在"));
        if (user.getUserType() != User.UserType.STUDENT) throw new BusinessException("只能添加学生账号");
        access.ensureStudent(classId, studentId);
    }

    public void removeStudent(Long classId, Long studentId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        students.deleteByClassIdAndUserId(classId, studentId);
    }

    public List<ClassTeacherRow> listTeachers(Long classId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        List<ClassTeacherRow> result = new ArrayList<>();
        for (ClassTeacher row : teachers.findByClassId(classId)) {
            users.findById(row.getTeacherId()).ifPresent(u -> result.add(ClassTeacherRow.builder()
                    .userId(u.getId()).username(u.getUsername()).realName(u.getRealName())
                    .role(row.getRole().name()).build()));
        }
        result.sort(Comparator.comparing(ClassTeacherRow::getRole).thenComparing(ClassTeacherRow::getRealName));
        return result;
    }

    public void addTeacher(Long classId, Long newTeacherId, Long actorId) {
        requireOwner(classId, actorId);
        User user = users.findById(newTeacherId).orElseThrow(() -> new BusinessException("教师不存在"));
        if (user.getUserType() != User.UserType.TEACHER || !Boolean.TRUE.equals(user.getActive()))
            throw new BusinessException("只能添加启用中的教师账号");
        access.addTeacher(classId, newTeacherId, ClassTeacher.Role.CO_TEACHER);
    }

    public void removeTeacher(Long classId, Long targetId, Long actorId) {
        requireOwner(classId, actorId);
        var target = teachers.findByClassIdAndTeacherId(classId, targetId)
                .orElseThrow(() -> new BusinessException("该教师不在班级中"));
        if (target.getRole() == ClassTeacher.Role.OWNER && teachers.countOwners(classId) <= 1)
            throw new BusinessException("班级至少需要保留一位负责教师");
        teachers.deleteByClassIdAndTeacherId(classId, targetId);
    }

    public List<ClassStudentRow> availableStudents(Long classId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        List<ClassStudentRow> result = new ArrayList<>();
        for (User u : users.findAll()) {
            if (u.getUserType() != User.UserType.STUDENT || !Boolean.TRUE.equals(u.getActive())) continue;
            if (students.findByUserId(u.getId()).isPresent()) continue;
            result.add(ClassStudentRow.builder().userId(u.getId()).username(u.getUsername())
                    .realName(u.getRealName()).className(u.getClassName()).active(true).build());
        }
        result.sort(Comparator.comparing(ClassStudentRow::getRealName));
        return result;
    }

    public List<ClassTeacherRow> availableTeachers(Long classId, Long teacherId) {
        access.requireTeacher(classId, teacherId);
        List<ClassTeacherRow> result = new ArrayList<>();
        for (User u : users.findAll()) {
            if (u.getUserType() != User.UserType.TEACHER || !Boolean.TRUE.equals(u.getActive())) continue;
            if (teachers.existsByClassIdAndTeacherId(classId, u.getId())) continue;
            result.add(ClassTeacherRow.builder().userId(u.getId()).username(u.getUsername())
                    .realName(u.getRealName()).role("CO_TEACHER").build());
        }
        result.sort(Comparator.comparing(ClassTeacherRow::getRealName));
        return result;
    }

    private void requireOwner(Long classId, Long teacherId) {
        var row = access.requireTeacher(classId, teacherId);
        if (row.getRole() != ClassTeacher.Role.OWNER) throw new BusinessException("只有班级负责教师可以管理教师");
    }

    private TeachingClassResponse toResponse(TeachingClass c, String role) {
        return TeachingClassResponse.builder().id(c.getId()).name(c.getName()).grade(c.getGrade())
                .status(c.getStatus().name()).studentCount(students.countByClassId(c.getId()))
                .teacherCount(teachers.findByClassId(c.getId()).size()).role(role).build();
    }
}
