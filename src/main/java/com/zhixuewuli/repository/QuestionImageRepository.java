package com.zhixuewuli.repository;

import com.zhixuewuli.entity.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuestionImageRepository extends JpaRepository<QuestionImage, String> {
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO question_images(content_hash,content,created_at,updated_at) " +
            "VALUES(:hash,:content,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) " +
            "ON DUPLICATE KEY UPDATE content_hash=content_hash", nativeQuery = true)
    int insertIfAbsent(@Param("hash") String hash, @Param("content") String content);
}
