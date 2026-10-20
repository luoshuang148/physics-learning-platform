package com.zhixuewuli.repository;

import com.zhixuewuli.entity.TeachingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeachingClassRepository extends JpaRepository<TeachingClass, Long> {
    Optional<TeachingClass> findByName(String name);
    List<TeachingClass> findByStatusOrderByNameAsc(TeachingClass.Status status);
}
