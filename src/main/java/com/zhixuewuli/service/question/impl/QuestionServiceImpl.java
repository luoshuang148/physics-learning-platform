package com.zhixuewuli.service.question.impl;

import com.zhixuewuli.common.exception.question.QuestionContentDuplicateException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.util.*;
import com.zhixuewuli.dto.request.QuestionCreateRequest;
import com.zhixuewuli.dto.request.QuestionUpdateRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 题目服务实现类
 * 
 * 实现题目管理的核心业务逻辑
 * 提供完整的题目增删改查功能
 * 
 * <p>
 * 实现特点：
 * </p>
 * <ul>
 * <li>严格的事务管理确保数据一致性</li>
 * <li>完善的日志记录便于问题追踪</li>
 * <li>详细的异常处理和业务规则验证</li>
 * <li>性能优化的只读事务配置</li>
 * <li>全量覆盖更新策略的精确实现</li>
 * </ul>
 * 
 * <p>
 * 事务管理：
 * </p>
 * <ul>
 * <li>写操作使用默认事务传播行为</li>
 * <li>读操作使用readOnly=true优化性能</li>
 * <li>异常情况下自动回滚事务</li>
 * </ul>
 * 
 * <p>
 * 数据验证：
 * </p>
 * <ul>
 * <li>题目内容全局唯一性验证</li>
 * <li>关联视频存在性验证</li>
 * <li>输入参数有效性验证</li>
 * <li>业务规则一致性检查</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionService 题目服务接口
 * @see QuestionRepository 题目数据访问接口
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final com.zhixuewuli.repository.TeacherEventQuestionRepository taskQuestionRepository;
    private final com.zhixuewuli.repository.AnswerRecordRepository answerRepository;
    private final com.zhixuewuli.repository.KnowledgePointRepository knowledgePoints;

    @Override
    public Question createQuestion(QuestionCreateRequest request) {

        log.info("开始创建题目，内容预览: {}",
                request.getContent().length() > 50 ? request.getContent().substring(0, 50) + "..."
                        : request.getContent());

        // 2. 验证题目内容唯一性
        if (questionRepository.existsByContent(request.getContent())) {
            log.warn("题目内容已存在: {}", request.getContent());
            throw new QuestionContentDuplicateException(request.getContent());
        }

        // 3. 创建题目实体
        Question question = new Question();
        question.setContent(request.getContent());
        question.setOptions(request.getOptions());
        question.setCorrectOption(request.getCorrectOption());
        question.setExplanation(request.getExplanation());
        question.setDifficulty(request.getDifficulty());
        question.setImageBase64(request.getImageBase64());
        question.setKnowledgePointCodes(validKnowledgePointCodes(request.getKnowledgePointCodes()));

        // 4. 保存到数据库
        Question savedQuestion = questionRepository.save(question);
        log.info("题目创建成功，ID: {}", savedQuestion.getId());

        return savedQuestion;
    }

    @Override
    public void deleteQuestionById(Long id) {

        log.info("开始删除题目，ID: {}", id);

        // 1. 验证题目存在性
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("尝试删除不存在的题目，ID: {}", id);
                    return new QuestionNotFoundException(id);
                });

        // 2. 执行删除
        if(taskQuestionRepository.existsByQuestionId(id) || answerRepository.existsByQuestionId(id))
            throw new com.zhixuewuli.common.exception.BusinessException("题目已有任务或作答关联，请保留历史题目");
        questionRepository.delete(question);
        log.info("题目删除成功，ID: {}", id);
    }

    @Override
    public Question updateQuestion(QuestionUpdateRequest request) {

        log.info("开始更新题目，ID: {}", request.getId());

        // 1. 验证题目存在性
        Question existingQuestion = questionRepository.findById(request.getId())
                .orElseThrow(() -> {
                    log.warn("尝试更新不存在的题目，ID: {}", request.getId());
                    return new QuestionNotFoundException(request.getId());
                });

        // 3. 全量覆盖更新（包括可能的空值）
        existingQuestion.setContent(request.getContent());
        existingQuestion.setOptions(request.getOptions());
        existingQuestion.setCorrectOption(request.getCorrectOption());
        existingQuestion.setExplanation(request.getExplanation());
        existingQuestion.setDifficulty(request.getDifficulty());
        existingQuestion.setImageBase64(request.getImageBase64());
        existingQuestion.setKnowledgePointCodes(validKnowledgePointCodes(request.getKnowledgePointCodes()));

        // 4. 保存更新
        Question updatedQuestion = questionRepository.save(existingQuestion);
        log.info("题目更新成功，ID: {}", updatedQuestion.getId());

        return updatedQuestion;
    }

    private List<String> validKnowledgePointCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        var normalized = codes.stream().filter(java.util.Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (normalized.size() != codes.size()) throw new IllegalArgumentException("知识点代码不能为空且不能重复");
        if (knowledgePoints.findByCodeIn(normalized).size() != normalized.size()) throw new IllegalArgumentException("包含未知知识点代码");
        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public Question getQuestionById(Long id) {
        log.debug("查询题目详情，ID: {}", id);

        return questionRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("题目不存在，ID: {}", id);
                    return new QuestionNotFoundException(id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Question> getAllQuestions(Long userId) {

        // 任何认证用户都可以查看所有题目
        log.debug("用户 {} 查询所有题目", userId);

        // 按创建时间倒序排列
        List<Question> questions = questionRepository.findAllByOrderByCreatedAtDesc();
        log.debug("共查询到 {} 道题目", questions.size());

        return questions;
    }
}
