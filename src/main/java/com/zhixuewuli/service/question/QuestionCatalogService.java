package com.zhixuewuli.service.question;

import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.dto.response.QuestionCatalogRow;
import com.zhixuewuli.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class QuestionCatalogService {
    private final QuestionRepository questions;

    @Transactional(readOnly=true)
    public PageResult<QuestionCatalogRow> page(int page, int size, String search, Integer difficulty) {
        if (page < 1 || size < 1 || size > 100 || (difficulty != null && (difficulty < 1 || difficulty > 5)))
            throw new IllegalArgumentException("页码须为正数，每页1到100条，难度1到5");
        String text = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        if (text.length() > 200) throw new IllegalArgumentException("搜索内容最多200字符");
        String pattern = "%" + text.replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        var result = questions.catalog(pattern, difficulty, PageRequest.of(page-1,size));
        return PageResult.<QuestionCatalogRow>builder().records(result.getContent()).total(result.getTotalElements())
                .pages(result.getTotalPages()).currentPage(page).pageSize(size).build();
    }
}
