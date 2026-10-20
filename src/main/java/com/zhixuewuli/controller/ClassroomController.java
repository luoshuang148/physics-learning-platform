package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.dto.request.ClassCreateRequest;
import com.zhixuewuli.dto.request.ClassMemberRequest;
import com.zhixuewuli.dto.response.*;
import com.zhixuewuli.service.classroom.ClassroomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teacher/classes")
@RequiredArgsConstructor
@TeacherOnly
public class ClassroomController {
    private final ClassroomService classroomService;

    @GetMapping
    public Result<List<TeachingClassResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(classroomService.listForTeacher(user.id()));
    }

    @PostMapping
    public Result<TeachingClassResponse> create(@Valid @RequestBody ClassCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.created("班级创建成功", classroomService.create(request, user.id()));
    }

    @GetMapping("/{id}/students")
    public Result<List<ClassStudentRow>> students(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(classroomService.listStudents(id, user.id()));
    }

    @GetMapping("/{id}/students/available")
    public Result<List<ClassStudentRow>> availableStudents(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(classroomService.availableStudents(id, user.id()));
    }

    @PostMapping("/{id}/students")
    public Result<Void> addStudent(@PathVariable Long id, @Valid @RequestBody ClassMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        classroomService.addStudent(id, request.getUserId(), user.id());
        return Result.success();
    }

    @DeleteMapping("/{id}/students/{userId}")
    public Result<Void> removeStudent(@PathVariable Long id, @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        classroomService.removeStudent(id, userId, user.id());
        return Result.success();
    }

    @GetMapping("/{id}/teachers")
    public Result<List<ClassTeacherRow>> teachers(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(classroomService.listTeachers(id, user.id()));
    }

    @GetMapping("/{id}/teachers/available")
    public Result<List<ClassTeacherRow>> availableTeachers(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(classroomService.availableTeachers(id, user.id()));
    }

    @PostMapping("/{id}/teachers")
    public Result<Void> addTeacher(@PathVariable Long id, @Valid @RequestBody ClassMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        classroomService.addTeacher(id, request.getUserId(), user.id());
        return Result.success();
    }

    @DeleteMapping("/{id}/teachers/{userId}")
    public Result<Void> removeTeacher(@PathVariable Long id, @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        classroomService.removeTeacher(id, userId, user.id());
        return Result.success();
    }
}
