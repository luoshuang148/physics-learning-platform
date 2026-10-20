package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ClassTeacher;
import com.zhixuewuli.entity.TeachingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    Optional<ClassTeacher> findByClassIdAndTeacherId(Long classId, Long teacherId);
    List<ClassTeacher> findByClassId(Long classId);
    List<ClassTeacher> findByTeacherId(Long teacherId);
    boolean existsByClassIdAndTeacherId(Long classId, Long teacherId);
    long countByClassIdAndRole(Long classId, ClassTeacher.Role role);
    void deleteByClassIdAndTeacherId(Long classId, Long teacherId);

    @Query("select c from TeachingClass c where c.id in " +
            "(select ct.classId from ClassTeacher ct where ct.teacherId=:teacherId) and c.status=com.zhixuewuli.entity.TeachingClass.Status.ACTIVE order by c.name")
    List<TeachingClass> findActiveClassesForTeacher(@Param("teacherId") Long teacherId);

    @Query("select count(ct) from ClassTeacher ct where ct.classId=:classId and ct.role=com.zhixuewuli.entity.ClassTeacher.Role.OWNER")
    long countOwners(@Param("classId") Long classId);
}
