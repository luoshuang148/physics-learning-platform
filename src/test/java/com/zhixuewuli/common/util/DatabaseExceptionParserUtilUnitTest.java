package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.auth.RealNameDuplicateException;
import com.zhixuewuli.common.exception.auth.UserNameDuplicateException;
import com.zhixuewuli.common.exception.question.QuestionContentDuplicateException;
import com.zhixuewuli.common.exception.video.VideoAlreadyExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * DatabaseExceptionParserUtil 单元测试类
 * 
 * <p>
 * 测试数据库异常解析工具类的核心功能，包括：
 * </p>
 * <ul>
 * <li>MySQL 重复条目异常解析 - 验证唯一约束违反的解析</li>
 * <li>MySQL 外键约束异常解析 - 验证外键约束违反的解析</li>
 * <li>MySQL 非空约束异常解析 - 验证非空约束违反的解析</li>
 * <li>H2 重复条目异常解析 - 验证 H2 数据库唯一约束违反的解析</li>
 * <li>未知异常处理 - 验证无法解析异常时的默认处理</li>
 * <li>业务异常抛出 - 验证根据字段名抛出对应业务异常</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 Mockito 模拟 DataIntegrityViolationException</li>
 * <li>覆盖所有支持的数据库异常类型</li>
 * <li>验证解析结果的友好消息、字段名、约束类型</li>
 * <li>测试 throwAppropriateBusinessException 的各种字段场景</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("DatabaseExceptionParserUtil 单元测试")
class DatabaseExceptionParserUtilUnitTest extends BaseUnitTest {

    // ==================== MySQL 重复条目异常解析测试 ====================

    @Nested
    @DisplayName("parseException - MySQL 重复条目异常解析")
    class ParseMySqlDuplicateEntryTests {

        @Test
        @DisplayName("TC-DB-01: 应该成功解析 MySQL 标准重复条目异常 - users 表 username 字段")
        void testParse_MySqlDuplicateEntry_StandardFormat() {
            // Given
            String errorMessage = "Duplicate entry 'testuser' for key 'users.username'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("用户名已存在：testuser");
            assertThat(info.getDuplicateValue()).isEqualTo("testuser");
            assertThat(info.getFieldName()).isEqualTo("username");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }

        @Test
        @DisplayName("TC-DB-02: 应该成功解析带库前缀的重复条目异常")
        void testParse_MySqlDuplicateEntry_WithDatabasePrefix() {
            // Given
            String errorMessage = "Duplicate entry 'zhangsan' for key 'zhixuewuli.users.real_name'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("姓名已存在：zhangsan");
            assertThat(info.getDuplicateValue()).isEqualTo("zhangsan");
            assertThat(info.getFieldName()).isEqualTo("real_name");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }

        @Test
        @DisplayName("TC-DB-03: 应该成功解析 UK_格式的唯一键")
        void testParse_MySqlDuplicateEntry_UKFormat() {
            // Given
            String errorMessage = "Duplicate entry 'unique_title' for key 'UK_TITLE'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("标题已存在：unique_title");
            assertThat(info.getDuplicateValue()).isEqualTo("unique_title");
            assertThat(info.getFieldName()).isEqualTo("title");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }

        @Test
        @DisplayName("TC-DB-04: 应该成功解析包含特殊字符的重复值")
        void testParse_MySqlDuplicateEntry_WithSpecialCharacters() {
            // Given
            String errorMessage = "Duplicate entry 'O\\'Reilly' for key 'users.username'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getDuplicateValue()).isEqualTo("O'Reilly");
            assertThat(info.getFieldName()).isEqualTo("username");
        }

        @Test
        @DisplayName("TC-DB-05: 应该成功解析题目内容重复异常")
        void testParse_MySqlDuplicateEntry_QuestionContent() {
            // Given
            String errorMessage = "Duplicate entry '牛顿第二定律的应用' for key 'questions.content'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("题目内容已存在：牛顿第二定律的应用");
            assertThat(info.getDuplicateValue()).isEqualTo("牛顿第二定律的应用");
            assertThat(info.getFieldName()).isEqualTo("content");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }

        @Test
        @DisplayName("TC-DB-06: 应该成功解析视频 URL 重复异常")
        void testParse_MySqlDuplicateEntry_VideoUrl() {
            // Given
            String errorMessage = "Duplicate entry 'https://example.com/video.mp4' for key 'videos.url'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("视频播放链接已存在：https://example.com/video.mp4");
            assertThat(info.getDuplicateValue()).isEqualTo("https://example.com/video.mp4");
            assertThat(info.getFieldName()).isEqualTo("url");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }
    }

    // ==================== MySQL 外键约束异常解析测试 ====================

    @Nested
    @DisplayName("parseException - MySQL 外键约束异常解析")
    class ParseMySqlForeignKeyTests {

        @Test
        @DisplayName("TC-DB-07: 应该成功解析 MySQL 外键约束违反 - users 表")
        void testParse_MySqlForeignKey_UsersTable() {
            // Given
            String errorMessage = "Cannot add or update a child row: a foreign key constraint fails ('zhixuewuli.users')";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("关联数据不存在或已被删除");
            assertThat(info.getFieldName()).isEqualTo("user_id");
            assertThat(info.getConstraintType()).isEqualTo("FOREIGN_KEY");
        }

        @Test
        @DisplayName("TC-DB-08: 应该成功解析 MySQL 外键约束违反 - questions 表")
        void testParse_MySqlForeignKey_QuestionsTable() {
            // Given
            String errorMessage = "Cannot add or update a child row: a foreign key constraint fails ('questions')";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("关联数据不存在或已被删除");
            assertThat(info.getFieldName()).isEqualTo("question_id");
            assertThat(info.getConstraintType()).isEqualTo("FOREIGN_KEY");
        }
    }

    // ==================== MySQL 非空约束异常解析测试 ====================

    @Nested
    @DisplayName("parseException - MySQL 非空约束异常解析")
    class ParseMySqlNotNullTests {

        @Test
        @DisplayName("TC-DB-10: 应该成功解析 MySQL 非空约束违反 - username 字段")
        void testParse_MySqlNotNull_UsernameField() {
            // Given
            String errorMessage = "Column 'username' cannot be null";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("用户名不能为空");
            assertThat(info.getFieldName()).isEqualTo("username");
            assertThat(info.getConstraintType()).isEqualTo("NOT_NULL");
        }

        @Test
        @DisplayName("TC-DB-11: 应该成功解析 MySQL 非空约束违反 - real_name 字段")
        void testParse_MySqlNotNull_RealNameField() {
            // Given
            String errorMessage = "Column 'real_name' cannot be null";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("姓名不能为空");
            assertThat(info.getFieldName()).isEqualTo("real_name");
            assertThat(info.getConstraintType()).isEqualTo("NOT_NULL");
        }

        @Test
        @DisplayName("TC-DB-12: 应该成功解析 MySQL 非空约束违反 - content 字段")
        void testParse_MySqlNotNull_ContentField() {
            // Given
            String errorMessage = "Column 'content' cannot be null";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("题目内容不能为空");
            assertThat(info.getFieldName()).isEqualTo("content");
            assertThat(info.getConstraintType()).isEqualTo("NOT_NULL");
        }

        @Test
        @DisplayName("TC-DB-13: 应该成功解析带表名前缀的非空约束异常")
        void testParse_MySqlNotNull_WithTablePrefix() {
            // Given
            String errorMessage = "Column 'users.username' cannot be null";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("用户名不能为空");
            assertThat(info.getFieldName()).isEqualTo("username");
            assertThat(info.getConstraintType()).isEqualTo("NOT_NULL");
        }
    }

    // ==================== H2 重复条目异常解析测试 ====================

    @Nested
    @DisplayName("parseException - H2 重复条目异常解析")
    class ParseH2DuplicateEntryTests {

        @Test
        @DisplayName("TC-DB-14: 应该成功解析 H2 唯一索引违反")
        void testParse_H2DuplicateEntry_UniqueIndex() {
            // Given
            String errorMessage = "Unique index or primary key violation: \"PUBLIC.UK_USERNAME ON PUBLIC.USERS(USERNAME) VALUES ('testuser')\"";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("用户名已存在：testuser");
            assertThat(info.getDuplicateValue()).isEqualTo("testuser");
            assertThat(info.getFieldName()).isEqualTo("username");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }

        @Test
        @DisplayName("TC-DB-15: 应该成功解析 H2 主键违反")
        void testParse_H2DuplicateEntry_PrimaryKey() {
            // Given
            String errorMessage = "Unique index or primary key violation: \"PUBLIC.CONSTRAINT_INDEX_1 ON PUBLIC.VIDEOS(URL) VALUES ('https://example.com/video.mp4')\"";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("视频播放链接已存在：https://example.com/video.mp4");
            assertThat(info.getDuplicateValue()).isEqualTo("https://example.com/video.mp4");
            assertThat(info.getFieldName()).isEqualTo("url");
            assertThat(info.getConstraintType()).isEqualTo("UNIQUE");
        }
    }

    // ==================== 未知/无法解析异常处理测试 ====================

    @Nested
    @DisplayName("parseException - 未知/无法解析异常处理")
    class ParseUnknownExceptionTests {

        @Test
        @DisplayName("TC-DB-16: 应该返回默认信息 - 当异常消息为空时")
        void testParse_NullMessage() {
            // Given
            DataIntegrityViolationException exception = new DataIntegrityViolationException(null);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("数据库约束违反");
            assertThat(info.getFieldName()).isEqualTo("unknown");
            assertThat(info.getConstraintType()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("TC-DB-17: 应该返回默认信息 - 当异常消息为空字符串时")
        void testParse_EmptyMessage() {
            // Given
            DataIntegrityViolationException exception = new DataIntegrityViolationException("");

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("数据库约束违反");
            assertThat(info.getFieldName()).isEqualTo("unknown");
            assertThat(info.getConstraintType()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("TC-DB-18: 应该返回默认信息 - 当异常消息为空白字符串时")
        void testParse_BlankMessage() {
            // Given
            DataIntegrityViolationException exception = new DataIntegrityViolationException("   ");

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("数据库约束违反");
            assertThat(info.getFieldName()).isEqualTo("unknown");
            assertThat(info.getConstraintType()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("TC-DB-19: 应该返回通用约束信息 - 当无法解析异常格式时")
        void testParse_UnrecognizedFormat() {
            // Given
            String errorMessage = "Some unknown database error occurred";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("数据约束违反，请检查输入内容");
            assertThat(info.getFieldName()).isEqualTo("unknown");
            assertThat(info.getConstraintType()).isEqualTo("UNKNOWN");
        }
    }

    // ==================== throwAppropriateBusinessException 测试
    // ====================

    @Nested
    @DisplayName("throwAppropriateBusinessException - 抛出对应业务异常")
    class ThrowBusinessExceptionTests {

        @Test
        @DisplayName("TC-DB-20: 应该抛出 UserNameDuplicateException - 当 username 重复时")
        void testThrowBusinessException_UsernameDuplicate() {
            // Given
            String errorMessage = "Duplicate entry 'duplicate_user' for key 'users.username'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(UserNameDuplicateException.class)
                    .hasMessageContaining("duplicate_user");
        }

        @Test
        @DisplayName("TC-DB-21: 应该抛出 RealNameDuplicateException - 当 real_name 重复时")
        void testThrowBusinessException_RealNameDuplicate() {
            // Given
            String errorMessage = "Duplicate entry '张三' for key 'users.real_name'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(RealNameDuplicateException.class)
                    .hasMessageContaining("张三");
        }

        @Test
        @DisplayName("TC-DB-22: 应该抛出 QuestionContentDuplicateException - 当 content 重复时")
        void testThrowBusinessException_QuestionContentDuplicate() {
            // Given
            String errorMessage = "Duplicate entry '题目内容 123' for key 'questions.content'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(QuestionContentDuplicateException.class)
                    .hasMessageContaining("题目内容 123");
        }

        @Test
        @DisplayName("TC-DB-23: 应该抛出 VideoAlreadyExistsException (标题) - 当 title 重复时")
        void testThrowBusinessException_VideoTitleDuplicate() {
            // Given
            String errorMessage = "Duplicate entry '教学视频标题' for key 'videos.title'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(VideoAlreadyExistsException.class)
                    .hasMessageContaining("标题")
                    .hasMessageContaining("教学视频标题");
        }

        @Test
        @DisplayName("TC-DB-24: 应该抛出 VideoAlreadyExistsException (播放链接) - 当 url 重复时")
        void testThrowBusinessException_VideoUrlDuplicate() {
            // Given
            String errorMessage = "Duplicate entry 'https://video.com/test.mp4' for key 'videos.url'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(VideoAlreadyExistsException.class)
                    .hasMessageContaining("播放链接")
                    .hasMessageContaining("https://video.com/test.mp4");
        }

        @Test
        @DisplayName("TC-DB-25: 应该抛出 BusinessException - 当其他字段重复时（通用处理）")
        void testThrowBusinessException_OtherFieldsDuplicate() {
            // Given
            String errorMessage = "Duplicate entry 'some_value' for key 'table.other_field'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When & Then
            assertThatThrownBy(() -> DatabaseExceptionParserUtil.throwAppropriateBusinessException(exception))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== DatabaseConstraintInfo Getter 测试 ====================

    @Nested
    @DisplayName("DatabaseConstraintInfo - Getter 方法测试")
    class DatabaseConstraintInfoGetterTests {

        @Test
        @DisplayName("TC-DB-26: 应该正确返回所有属性值")
        void testDatabaseConstraintInfo_Getters() {
            // Given
            String friendlyMessage = "测试消息";
            String duplicateValue = "test_value";
            String fieldName = "test_field";
            String constraintType = "UNIQUE";

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = new DatabaseExceptionParserUtil.DatabaseConstraintInfo(
                    friendlyMessage, duplicateValue, fieldName, constraintType);

            // Then
            assertThat(info.getFriendlyMessage()).isEqualTo(friendlyMessage);
            assertThat(info.getDuplicateValue()).isEqualTo(duplicateValue);
            assertThat(info.getFieldName()).isEqualTo(fieldName);
            assertThat(info.getConstraintType()).isEqualTo(constraintType);
        }
    }

    // ==================== 边界和异常场景测试 ====================

    @Nested
    @DisplayName("边界和异常场景测试")
    class BoundaryAndExceptionScenarios {

        @Test
        @DisplayName("TC-DB-27: 应该处理解析过程中的异常并返回安全信息")
        void testParse_ExceptionDuringParsing() {
            // Given - 构造一个可能导致解析异常的极端情况
            String errorMessage = "Malformed exception message that could cause parsing issues "
                    + "Duplicate entry 'test' for key without proper format";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then - 应该返回通用的约束违反信息而不是抛出异常
            assertThat(info).isNotNull();
            assertThat(info.getFriendlyMessage()).isEqualTo("数据约束违反，请检查输入内容");
        }

        @Test
        @DisplayName("TC-DB-28: 应该处理非常长的异常消息")
        void testParse_VeryLongErrorMessage() {
            // Given
            String longValue = "a".repeat(1000); // 1000 个字符的重复值
            String errorMessage = "Duplicate entry '" + longValue + "' for key 'table.field'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getDuplicateValue()).isEqualTo(longValue);
            assertThat(info.getFieldName()).isEqualTo("field");
        }

        @Test
        @DisplayName("TC-DB-29: 应该处理包含换行符的异常消息")
        void testParse_ErrorMessageWithNewlines() {
            // Given
            String errorMessage = "Duplicate entry 'test\nvalue' for key 'table.field'";
            DataIntegrityViolationException exception = new DataIntegrityViolationException(errorMessage);

            // When
            DatabaseExceptionParserUtil.DatabaseConstraintInfo info = DatabaseExceptionParserUtil
                    .parseException(exception);

            // Then
            assertThat(info).isNotNull();
            assertThat(info.getDuplicateValue()).contains("test");
        }
    }
}
