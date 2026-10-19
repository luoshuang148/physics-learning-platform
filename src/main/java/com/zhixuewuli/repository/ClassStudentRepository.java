package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ClassStudent;
import com.zhixuewuli.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {
    Optional<ClassStudent> findByUserId(Long userId);
    List<ClassStudent> findByClassId(Long classId);
    boolean existsByClassIdAndUserId(Long classId, Long userId);
    long countByClassId(Long classId);
    void deleteByClassIdAndUserId(Long classId, Long userId);

    @Query("select u from User u where u.id in (select cs.userId from ClassStudent cs where cs.classId=:classId) " +
            "and u.active=true and u.userType=com.zhixuewuli.entity.User.UserType.STUDENT order by u.realName")
    List<User> findActiveStudents(@Param("classId") Long classId);
}
