package com.zhixuewuli.repository;

import com.zhixuewuli.entity.TeachingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeachingClassRepository extends JpaRepository<TeachingClass, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from TeachingClass c where c.id=:id")
    Optional<TeachingClass> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<TeachingClass> findByName(String name);
    List<TeachingClass> findByStatusOrderByNameAsc(TeachingClass.Status status);
}
