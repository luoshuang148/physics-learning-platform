package com.zhixuewuli.service.experiment;

import com.zhixuewuli.dto.request.ExperimentCreateRequest;
import com.zhixuewuli.dto.request.ExperimentUpdateRequest;
import com.zhixuewuli.entity.Experiment;

import java.util.List;

/**
 * 实验配置服务接口
 * 
 * <p>
 * 提供实验配置管理的核心业务逻辑接口。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>实验创建与名称唯一性验证</li>
 * <li>实验删除（根据 ID）</li>
 * <li>实验更新（增量覆盖策略）</li>
 * <li>实验查询（单个和批量）</li>
 * <li>题目ID 存在性验证</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public interface ExperimentService {
    
    /**
     * 创建新实验
     * 
     * <p>
     * 根据提供的实验信息创建新的实验记录。
     * </p>
     * 
     * @param request 实验创建请求
     * @param teacherId 教师 ID
     * @return 创建的实验实体
     */
    Experiment createExperiment(ExperimentCreateRequest request, Long teacherId);
    
    /**
     * 根据 ID 删除实验
     * 
     * <p>
     * 删除指定 ID 的实验记录。
     * </p>
     * 
     * @param id 实验ID
     * @param teacherId 教师 ID
     */
    void deleteExperimentById(Long id, Long teacherId);
    
    /**
     * 更新实验信息（增量覆盖）
     * 
     * <p>
     * 根据实验ID 增量更新实验信息。
     * 只更新请求中提供的非空字段。
     * </p>
     * 
     * @param request 实验更新请求
     * @param teacherId 教师 ID
     * @return 更新后的实验实体
     */
    Experiment updateExperiment(ExperimentUpdateRequest request, Long teacherId);
    
    /**
     * 根据 ID 获取实验
     * 
     * <p>
     * 根据实验ID 获取实验的详细信息。
     * 此方法无需身份认证。
     * </p>
     * 
     * @param id 实验ID
     * @return 实验实体
     */
    Experiment getExperimentById(Long id);
    
    /**
     * 获取所有实验
     * 
     * <p>
     * 获取数据库中所有的实验记录。
     * 按序号正序排列。
     * </p>
     * 
     * @return 实验列表，按 orderNumber 升序排列
     */
    List<Experiment> getAllExperiments();
}
