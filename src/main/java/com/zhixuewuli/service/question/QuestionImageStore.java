package com.zhixuewuli.service.question;

import com.zhixuewuli.entity.QuestionImage;
import com.zhixuewuli.repository.QuestionImageRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores question images once by content hash. Answer rows keep only the hash,
 * so editing the bank cannot change a historical image.
 */
@Component
@RequiredArgsConstructor
public class QuestionImageStore {
    private final QuestionImageRepository images;

    @Transactional
    public String store(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) return null;
        String hash = DigestUtils.sha256Hex(imageBase64);
        if (!images.existsById(hash)) {
            images.insertIfAbsent(hash, imageBase64);
        }
        return hash;
    }

    @Transactional(readOnly = true)
    public String load(String hash) {
        if (hash == null || hash.isBlank()) return null;
        return images.findById(hash).map(QuestionImage::getContent).orElse(null);
    }
}
