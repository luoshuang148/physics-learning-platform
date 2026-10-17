package com.zhixuewuli.service.question;

import com.zhixuewuli.common.exception.question.QuestionContentDuplicateException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionVideoNotFoundException;
import com.zhixuewuli.dto.request.QuestionCreateRequest;
import com.zhixuewuli.dto.request.QuestionUpdateRequest;
import com.zhixuewuli.entity.Question;

import java.util.List;

/**
 * 题目服务接口
 * 
 * 提供题目管理的核心业务逻辑接口
 * 包含题目的增删改查等完整功能
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>题目创建与唯一性验证</li>
 * <li>题目删除（根据ID）</li>
 * <li>题目更新（全量覆盖策略）</li>
 * <li>题目查询（单个和批量）</li>
 * <li>数据完整性保障</li>
 * </ul>
 * 
 * <p>
 * 业务规则：
 * </p>
 * <ul>
 * <li>题目内容必须全局唯一</li>
 * <li>必须关联有效的视频记录</li>
 * <li>更新操作采用全量覆盖策略</li>
 * <li>所有操作都有完整的日志记录</li>
 * </ul>
 * 
 * <p>
 * 异常处理：
 * </p>
 * <ul>
 * <li>{@link QuestionNotFoundException} - 题目不存在</li>
 * <li>{@link QuestionContentDuplicateException} - 题目内容重复</li>
 * <li>{@link QuestionVideoNotFoundException} - 关联视频不存在</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see Question 题目实体类
 * @see QuestionCreateRequest 题目创建请求DTO
 * @see QuestionUpdateRequest 题目更新请求DTO
 */
public interface QuestionService {

    /**
     * 创建新题目
     * 
     * 根据提供的题目信息创建新的题目记录。
     * 会验证题目内容的唯一性和关联视频的存在性。
     * 
     * @param request 题目创建请求
     * @return 创建的题目实体
     * @throws QuestionContentDuplicateException 当题目内容已存在时抛出
     * @throws QuestionVideoNotFoundException    当关联视频不存在时抛出
     * 
     * @implSpec 验证题目内容的全局唯一性
     * @implSpec 验证关联视频的存在性
     * @implSpec 记录创建操作日志
     * @implSpec 返回完整的题目实体信息
     */
    Question createQuestion(QuestionCreateRequest request, Long teacherId);

    /**
     * 根据ID删除题目
     * 
     * 删除指定ID的题目记录。
     * 如果题目不存在会抛出相应异常。
     * 
     * @param id 题目ID
     * @throws QuestionNotFoundException 当题目不存在时抛出
     * 
     * @implSpec 验证题目存在性
     * @implSpec 记录删除操作日志
     * @implSpec 支持级联删除关联数据（如答题记录）
     */
    void deleteQuestionById(Long id, Long teacherId);

    /**
     * 更新题目信息（全量覆盖）
     * 
     * 根据题目ID全量更新题目信息。
     * 更新请求中的所有字段都会覆盖原有数据，包括空值字段。
     * 
     * @param request 题目更新请求
     * @return 更新后的题目实体
     * @throws QuestionNotFoundException         当题目不存在时抛出
     * @throws QuestionContentDuplicateException 当新题目内容已存在时抛出
     * @throws QuestionVideoNotFoundException    当新的关联视频不存在时抛出
     * 
     * @implSpec 验证题目存在性
     * @implSpec 检查更新后题目内容的唯一性
     * @implSpec 验证新的关联视频存在性
     * @implSpec 采用全量覆盖更新策略
     * @implSpec 记录更新操作日志
     */
    Question updateQuestion(QuestionUpdateRequest request, Long teacherId);

    /**
     * 根据ID获取题目
     * 
     * 根据题目ID获取题目的详细信息。
     * 此方法无需身份认证，学生和教师都可以调用。
     * 
     * @param id 题目ID
     * @return 题目实体
     * @throws QuestionNotFoundException 当题目不存在时抛出
     * 
     * @implSpec 验证题目存在性
     * @implSpec 记录查询操作日志
     * @implSpec 只读事务优化性能
     */
    Question getQuestionById(Long id);

    /**
     * 获取所有题目
     * 
     * 获取数据库中所有的题目记录。
     * 按创建时间倒序排列，便于查看最新题目。
     * 
     * @return 题目列表，按创建时间倒序排列
     * 
     * @implSpec 只读事务优化性能
     * @implSpec 按创建时间倒序排序
     * @implSpec 记录批量查询日志
     */
    List<Question> getAllQuestions(Long teacherId);
}