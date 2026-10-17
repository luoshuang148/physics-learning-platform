package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.Experiment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 实验仓库集成测试
 * 验证 H2 数据库连接、基本 CRUD 操作和自定义查询方法
 */
@Tag("integration-repository")
class ExperimentRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExperimentRepository experimentRepository;

    // ==================== 基础 CRUD 测试 ====================

    @Test
    @Rollback
    void testSaveAndFindExperiment() {
        // Given - 创建实验
        Experiment experiment = new Experiment();
        experiment.setName("牛顿第一定律实验_" + System.currentTimeMillis());
        experiment.setQuestionIds("[3001,3002,3003]");
        experiment.setDescription("验证牛顿第一定律的实验");
        experiment.setOrderNumber(1);

        // When
        Experiment savedExperiment = experimentRepository.save(experiment);
        Optional<Experiment> foundExperiment = experimentRepository.findById(savedExperiment.getId());

        // Then
        assertThat(foundExperiment).isPresent();
        assertThat(foundExperiment.get().getName()).contains("牛顿第一定律实验");
        assertThat(foundExperiment.get().getQuestionIds()).isEqualTo("[3001,3002,3003]");
        assertThat(foundExperiment.get().getDescription()).contains("验证牛顿第一定律");
        assertThat(foundExperiment.get().getOrderNumber()).isEqualTo(1);
    }

    @Test
    @Rollback
    void testUpdateExperiment() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Experiment experiment = createExperiment("原始实验名称", 1);
        Long experimentId = experiment.getId();
        
        experiment.setName("更新后的实验名称");
        experiment.setQuestionIds("[3004,3005]");
        experiment.setDescription("更新后的描述");
        experiment.setOrderNumber(2);

        // When
        Experiment updatedExperiment = experimentRepository.save(experiment);

        // Then
        assertThat(updatedExperiment.getName()).isEqualTo("更新后的实验名称");
        assertThat(updatedExperiment.getQuestionIds()).isEqualTo("[3004,3005]");
        assertThat(updatedExperiment.getDescription()).isEqualTo("更新后的描述");
        assertThat(updatedExperiment.getOrderNumber()).isEqualTo(2);
    }

    @Test
    @Rollback
    void testDeleteExperiment() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Experiment experiment = createExperiment("待删除实验", 1);
        Long experimentId = experiment.getId();

        // When
        experimentRepository.deleteById(experimentId);
        Optional<Experiment> deletedExperiment = experimentRepository.findById(experimentId);

        // Then
        assertThat(deletedExperiment).isEmpty();
    }

    @Test
    @Rollback
    void testTimestamps() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Experiment experiment = createExperiment("时间戳测试实验", 1);

        // When
        Experiment savedExperiment = experimentRepository.save(experiment);

        // Then - 验证时间戳自动生成
        assertThat(savedExperiment.getCreatedAt()).isNotNull();
        assertThat(savedExperiment.getUpdatedAt()).isNotNull();
        assertThat(savedExperiment.getCreatedAt()).isBeforeOrEqualTo(savedExperiment.getUpdatedAt());
    }

    // ==================== 自定义查询方法测试 ====================

    @Test
    @Rollback
    void testFindByName() {
        // Given
        cleanTable("EXPERIMENTS");
        
        String uniqueName = "唯一名称实验_" + System.currentTimeMillis();
        Experiment experiment = createExperiment(uniqueName, 1);

        // When
        Optional<Experiment> found = experimentRepository.findByName(uniqueName);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(uniqueName);
    }

    @Test
    @Rollback
    void testExistsByName() {
        // Given
        cleanTable("EXPERIMENTS");
        
        String existingName = "存在性测试实验_" + System.currentTimeMillis();
        createExperiment(existingName, 1);

        // When
        boolean exists = experimentRepository.existsByName(existingName);
        boolean notExists = experimentRepository.existsByName("不存在的名称");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testFindByOrderNumber() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Integer orderNumber = 5;
        Experiment experiment = createExperiment("序号测试实验", orderNumber);

        // When
        Optional<Experiment> found = experimentRepository.findByOrderNumber(orderNumber);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderNumber()).isEqualTo(orderNumber);
    }

    @Test
    @Rollback
    void testExistsByOrderNumber() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Integer orderNumber = 10;
        createExperiment("序号存在性测试", orderNumber);

        // When
        boolean exists = experimentRepository.existsByOrderNumber(orderNumber);
        boolean notExists = experimentRepository.existsByOrderNumber(999);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testFindAllByOrderByOrderNumberAsc() {
        // Given - 清理数据并乱序创建实验
        cleanTable("EXPERIMENTS");

        Experiment exp1 = createExperiment("实验 C", 3);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Experiment exp2 = createExperiment("实验 A", 1);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Experiment exp3 = createExperiment("实验 B", 2);

        // When
        List<Experiment> experiments = experimentRepository.findAllByOrderByOrderNumberAsc();

        // Then - 验证按序号正序排序
        assertThat(experiments).hasSize(3);
        assertThat(experiments.get(0).getOrderNumber()).isEqualTo(1);
        assertThat(experiments.get(1).getOrderNumber()).isEqualTo(2);
        assertThat(experiments.get(2).getOrderNumber()).isEqualTo(3);
    }

    @Test
    @Rollback
    void testFindAllByOrderByCreatedAtDesc() {
        // Given - 清理数据并按顺序创建
        cleanTable("EXPERIMENTS");

        Experiment exp1 = createExperiment("第一个实验", 1);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Experiment exp2 = createExperiment("第二个实验", 2);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Experiment exp3 = createExperiment("第三个实验", 3);

        // When
        List<Experiment> experiments = experimentRepository.findAllByOrderByCreatedAtDesc();

        // Then - 验证按创建时间倒序排列（最新的在前）
        assertThat(experiments).hasSize(3);
        assertThat(experiments.get(0).getName()).startsWith("第三个实验");
        assertThat(experiments.get(1).getName()).startsWith("第二个实验");
        assertThat(experiments.get(2).getName()).startsWith("第一个实验");
    }

    // ==================== 唯一性约束测试 ====================

    @Test
    @Rollback
    void testNameUniquenessConstraint() {
        // Given
        cleanTable("EXPERIMENTS");
        
        String duplicateName = "重复名称实验_" + System.currentTimeMillis();
        Experiment exp1 = createExperiment(duplicateName, 1);

        // When & Then - 尝试保存同名实验应抛出异常
        Experiment exp2 = new Experiment();
        exp2.setName(duplicateName);
        exp2.setQuestionIds("[3001]");
        exp2.setOrderNumber(2);

        assertThatThrownBy(() -> experimentRepository.save(exp2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Rollback
    void testOrderNumberUniquenessConstraint() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Integer duplicateOrderNumber = 5;
        Experiment exp1 = createExperiment("实验 A", duplicateOrderNumber);

        // When & Then - 尝试保存同序号实验应抛出异常
        Experiment exp2 = new Experiment();
        exp2.setName("实验 B_" + System.currentTimeMillis());
        exp2.setQuestionIds("[3001]");
        exp2.setOrderNumber(duplicateOrderNumber);

        assertThatThrownBy(() -> experimentRepository.save(exp2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Rollback
    void testNameMaxLengthBoundary() {
        // Given - 最大长度 255 字符
        cleanTable("EXPERIMENTS");
        
        String maxName = "a".repeat(255);
        Experiment experiment = new Experiment();
        experiment.setName(maxName);
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(1);

        // When
        Experiment saved = experimentRepository.save(experiment);

        // Then - 应该成功保存
        assertThat(saved.getName()).hasSize(255);
    }

    @Test
    @Rollback
    void testNameExceedingMaxLength() {
        // Given - 超过 255 字符
        cleanTable("EXPERIMENTS");
        
        String tooLongName = "a".repeat(256);
        Experiment experiment = new Experiment();
        experiment.setName(tooLongName);
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(1);

        // When & Then - 应该抛出验证异常（Bean Validation）
        assertThatThrownBy(() -> experimentRepository.save(experiment))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    @Rollback
    void testOrderNumberMaxValue() {
        // Given - Integer.MAX_VALUE
        cleanTable("EXPERIMENTS");
        
        Experiment experiment = new Experiment();
        experiment.setName("最大序号实验_" + System.currentTimeMillis());
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(Integer.MAX_VALUE);

        // When
        Experiment saved = experimentRepository.save(experiment);

        // Then
        assertThat(saved.getOrderNumber()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @Rollback
    void testNegativeOrderNumber() {
        // Given - 负数序号
        cleanTable("EXPERIMENTS");
        
        Experiment experiment = new Experiment();
        experiment.setName("负数序号实验_" + System.currentTimeMillis());
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(-1);

        // When & Then - 应该抛出验证异常（@Min 约束）
        assertThatThrownBy(() -> experimentRepository.save(experiment))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    // ==================== 边界条件测试 ====================

    @Test
    @Rollback
    void testNotFoundReturnsEmpty() {
        // When
        Optional<Experiment> found = experimentRepository.findById(99999L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @Rollback
    void testExistsByNameReturnsFalse() {
        // When
        boolean exists = experimentRepository.existsByName("不存在的实验");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @Rollback
    void testDescriptionNullAndEmpty() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Experiment exp1 = new Experiment();
        exp1.setName("空描述实验 1_" + System.currentTimeMillis());
        exp1.setQuestionIds("[3001]");
        exp1.setOrderNumber(1);
        exp1.setDescription(null);

        Experiment exp2 = new Experiment();
        exp2.setName("空描述实验 2_" + System.currentTimeMillis());
        exp2.setQuestionIds("[3001]");
        exp2.setOrderNumber(2);
        exp2.setDescription("");

        // When
        Experiment saved1 = experimentRepository.save(exp1);
        Experiment saved2 = experimentRepository.save(exp2);

        // Then
        assertThat(saved1.getDescription()).isNull();
        assertThat(saved2.getDescription()).isEmpty();
    }

    @Test
    @Rollback
    void testQuestionIdsNullAndEmpty() {
        // Given
        cleanTable("EXPERIMENTS");
        
        Experiment exp1 = new Experiment();
        exp1.setName("空题目 ID 实验 1_" + System.currentTimeMillis());
        exp1.setQuestionIds(null);
        exp1.setOrderNumber(1);

        Experiment exp2 = new Experiment();
        exp2.setName("空题目 ID 实验 2_" + System.currentTimeMillis());
        exp2.setQuestionIds("[]");
        exp2.setOrderNumber(2);

        // When
        Experiment saved1 = experimentRepository.save(exp1);
        Experiment saved2 = experimentRepository.save(exp2);

        // Then
        assertThat(saved1.getQuestionIds()).isNull();
        assertThat(saved2.getQuestionIds()).isEqualTo("[]");
    }

    // ==================== 安全测试 ====================

    @Test
    @Rollback
    void testSqlInjectionInName() {
        // Given - SQL 注入尝试
        cleanTable("EXPERIMENTS");
        
        String sqlInjectionName = "test'; DROP TABLE experiments; --";
        Experiment experiment = new Experiment();
        experiment.setName(sqlInjectionName);
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(1);

        // When - JPA 自动参数化，SQL 注入会被转义
        Experiment saved = experimentRepository.save(experiment);

        // Then - 成功保存，但名称是普通字符串
        assertThat(saved.getName()).isEqualTo(sqlInjectionName);
        
        // 验证表没有被删除
        assertThat(experimentRepository.count()).isEqualTo(1);
    }

    @Test
    @Rollback
    void testXssScriptInDescription() {
        // Given - XSS 脚本尝试
        cleanTable("EXPERIMENTS");
        
        String xssScript = "<script>alert('xss')</script>";
        Experiment experiment = new Experiment();
        experiment.setName("XSS 测试实验_" + System.currentTimeMillis());
        experiment.setQuestionIds("[3001]");
        experiment.setOrderNumber(1);
        experiment.setDescription(xssScript);

        // When - 存储型 XSS，后端允许保存，前端负责防御
        Experiment saved = experimentRepository.save(experiment);

        // Then - 成功保存，XSS 脚本作为普通文本存储
        assertThat(saved.getDescription()).isEqualTo(xssScript);
    }

    // ==================== 辅助方法 ====================

    /**
     * 辅助方法：创建实验
     */
    private Experiment createExperiment(String name, Integer orderNumber) {
        Experiment experiment = new Experiment();
        experiment.setName(name);
        experiment.setQuestionIds("[3001,3002]");
        experiment.setDescription("测试实验描述");
        experiment.setOrderNumber(orderNumber);
        return experimentRepository.save(experiment);
    }
}
