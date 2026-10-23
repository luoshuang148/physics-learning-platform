package com.zhixuewuli.repository;

import com.zhixuewuli.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, String> {
    List<KnowledgePoint> findByCodeIn(Collection<String> codes);
    List<KnowledgePoint> findAllByOrderByTopicAscNameAsc();
}
