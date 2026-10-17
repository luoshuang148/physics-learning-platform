package com.zhixuewuli.service.experiment.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.experiment.ExperimentBusinessException;
import com.zhixuewuli.common.exception.experiment.ExperimentNameDuplicateException;
import com.zhixuewuli.common.exception.experiment.ExperimentNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.JsonArrayUtil;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.ExperimentCreateRequest;
import com.zhixuewuli.dto.request.ExperimentUpdateRequest;
import com.zhixuewuli.entity.Experiment;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.ExperimentRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.experiment.ExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 实验配置服务实现类
 * 
 * <p>
 * 实现实验配置管理的核心业务逻辑。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ExperimentServiceImpl implements ExperimentService {
    
    private final ExperimentRepository experimentRepository;
    private final QuestionRepository questionRepository;
    private final UserPermissionUtil userPermissionUtil;
    private final JsonArrayUtil jsonArrayUtil;
    private final ObjectMapper objectMapper;
    
    @Override
    public Experiment createExperiment(ExperimentCreateRequest request, Long teacherId) {
        // 1. 权限验证
        userPermissionUtil.checkTeacherPermission(teacherId);
        
        log.info("开始创建实验，名称：{}", request.getName());
        
        // 2. 验证实验名称唯一性
        if (experimentRepository.existsByName(request.getName())) {
            log.warn("实验名称已存在：{}", request.getName());
            throw new ExperimentNameDuplicateException(request.getName());
        }
        
        // 3. 验证序号唯一性
        if (experimentRepository.existsByOrderNumber(request.getOrderNumber())) {
            log.warn("实验序号已存在：{}", request.getOrderNumber());
            throw new ExperimentBusinessException(ResultCode.EXPERIMENT_ORDER_NUMBER_DUPLICATE,
                "实验序号已存在：" + request.getOrderNumber());
        }
        
        // 4. 验证并解析题目ID列表
        List<Long> questionIds = validateAndParseQuestionIds(request.getQuestionIds());
        
        // 5. 创建实验实体
        Experiment experiment = new Experiment();
        experiment.setName(request.getName());
        experiment.setQuestionIds(request.getQuestionIds());
        experiment.setDescription(request.getDescription());
        experiment.setOrderNumber(request.getOrderNumber());
        
        // 6. 保存到数据库
        Experiment savedExperiment = experimentRepository.save(experiment);
        log.info("实验创建成功，ID: {}", savedExperiment.getId());
        
        return savedExperiment;
    }
    
    @Override
    public void deleteExperimentById(Long id, Long teacherId) {
        // 1. 权限验证
        userPermissionUtil.checkTeacherPermission(teacherId);
        
        log.info("开始删除实验，ID: {}", id);
        
        // 2. 验证实验存在性
        if (!experimentRepository.existsById(id)) {
            log.warn("实验不存在：{}", id);
            throw new ExperimentNotFoundException(id);
        }
        
        // 3. 删除实验
        experimentRepository.deleteById(id);
        log.info("实验删除成功，ID: {}", id);
    }
    
    @Override
    public Experiment updateExperiment(ExperimentUpdateRequest request, Long teacherId) {
        // 1. 权限验证
        userPermissionUtil.checkTeacherPermission(teacherId);
        
        log.info("开始更新实验，ID: {}", request.getId());
        
        // 2. 获取现有实验
        Experiment experiment = experimentRepository.findById(request.getId())
            .orElseThrow(() -> {
                log.warn("实验不存在：{}", request.getId());
                return new ExperimentNotFoundException(request.getId());
            });
        
        // 3. 增量更新（只更新非空字段）
        if (request.getName() != null) {
            // 验证名称唯一性（排除自己）
            if (!experiment.getName().equals(request.getName()) && 
                experimentRepository.existsByName(request.getName())) {
                log.warn("实验名称已存在：{}", request.getName());
                throw new ExperimentNameDuplicateException(request.getName());
            }
            experiment.setName(request.getName());
        }
        
        if (request.getQuestionIds() != null) {
            // 验证并解析题目ID列表
            validateAndParseQuestionIds(request.getQuestionIds());
            experiment.setQuestionIds(request.getQuestionIds());
        }
        
        if (request.getDescription() != null) {
            experiment.setDescription(request.getDescription());
        }
        
        if (request.getOrderNumber() != null) {
            // 验证序号唯一性（排除自己）
            if (!experiment.getOrderNumber().equals(request.getOrderNumber()) &&
                experimentRepository.existsByOrderNumber(request.getOrderNumber())) {
                log.warn("实验序号已存在：{}", request.getOrderNumber());
                throw new ExperimentBusinessException(ResultCode.EXPERIMENT_ORDER_NUMBER_DUPLICATE,
                    "实验序号已存在：" + request.getOrderNumber());
            }
            experiment.setOrderNumber(request.getOrderNumber());
        }
        
        // 4. 保存
        Experiment updatedExperiment = experimentRepository.save(experiment);
        log.info("实验更新成功，ID: {}", updatedExperiment.getId());
        
        return updatedExperiment;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Experiment getExperimentById(Long id) {
        log.info("查询实验，ID: {}", id);
        
        return experimentRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("实验不存在：{}", id);
                return new ExperimentNotFoundException(id);
            });
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Experiment> getAllExperiments() {
        log.info("查询所有实验");
        
        // 按序号正序排序
        return experimentRepository.findAllByOrderByOrderNumberAsc();
    }
    
    /**
     * 验证并解析题目ID列表
     * 
     * @param questionIdsJson JSON 格式的题目ID列表
     * @return 题目ID列表
     */
    private List<Long> validateAndParseQuestionIds(String questionIdsJson) {
        try {
            // 1. 解析 JSON
            List<Long> questionIds = jsonArrayUtil.parseQuestionIds(questionIdsJson);
            
            // 2. 验证每个题目ID 是否存在
            for (Long questionId : questionIds) {
                if (!questionRepository.existsById(questionId)) {
                    log.warn("题目ID 不存在：{}", questionId);
                    throw new QuestionNotFoundException(questionId);
                }
            }
            
            return questionIds;
        } catch (ExperimentBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("题目ID列表解析失败：{}", questionIdsJson, e);
            throw new ExperimentBusinessException(ResultCode.INVALID_QUESTION_IDS_FORMAT,
                "题目ID列表格式无效：" + e.getMessage());
        }
    }
}
