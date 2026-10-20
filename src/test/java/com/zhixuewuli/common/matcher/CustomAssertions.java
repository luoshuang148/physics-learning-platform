package com.zhixuewuli.common.matcher;

import org.assertj.core.api.AbstractAssert;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.AnswerRecord;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 自定义断言匹配器
 * 提供针对业务实体的专门断言方法
 */
public class CustomAssertions {

    /**
     * 用户实体断言
     */
    public static UserAssert assertThat(User user) {
        return new UserAssert(user);
    }

    /**
     * 题目实体断言
     */
    public static QuestionAssert assertThat(Question question) {
        return new QuestionAssert(question);
    }

    /**
     * 答题记录断言
     */
    public static AnswerRecordAssert assertThat(AnswerRecord answerRecord) {
        return new AnswerRecordAssert(answerRecord);
    }

    /**
     * 用户断言类
     */
    public static class UserAssert extends AbstractAssert<UserAssert, User> {

        public UserAssert(User user) {
            super(user, UserAssert.class);
        }

        public UserAssert hasUsername(String expected) {
            isNotNull();
            if (!Objects.equals(actual.getUsername(), expected)) {
                failWithMessage("Expected user username to be <%s> but was <%s>",
                        expected, actual.getUsername());
            }
            return this;
        }

        public UserAssert hasRealName(String expected) {
            isNotNull();
            if (!Objects.equals(actual.getRealName(), expected)) {
                failWithMessage("Expected user real name to be <%s> but was <%s>",
                        expected, actual.getRealName());
            }
            return this;
        }

        public UserAssert hasUserType(User.UserType expected) {
            isNotNull();
            if (!Objects.equals(actual.getUserType(), expected)) {
                failWithMessage("Expected user type to be <%s> but was <%s>",
                        expected, actual.getUserType());
            }
            return this;
        }

        public UserAssert isActive() {
            isNotNull();
            if (!Boolean.TRUE.equals(actual.getActive())) {
                failWithMessage("Expected user to be active but was inactive");
            }
            return this;
        }
    }

    /**
     * 题目断言类
     */
    public static class QuestionAssert extends AbstractAssert<QuestionAssert, Question> {

        public QuestionAssert(Question question) {
            super(question, QuestionAssert.class);
        }

        public QuestionAssert hasContent(String expected) {
            isNotNull();
            if (!Objects.equals(actual.getContent(), expected)) {
                failWithMessage("Expected question content to be <%s> but was <%s>",
                        expected, actual.getContent());
            }
            return this;
        }

        public QuestionAssert hasCorrectOption(String expected) {
            isNotNull();
            if (!Objects.equals(actual.getCorrectOption(), expected)) {
                failWithMessage("Expected correct option to be <%s> but was <%s>",
                        expected, actual.getCorrectOption());
            }
            return this;
        }

        public QuestionAssert hasDifficulty(int expected) {
            isNotNull();
            if (actual.getDifficulty() != expected) {
                failWithMessage("Expected difficulty to be <%d> but was <%d>",
                        expected, actual.getDifficulty());
            }
            return this;
        }
    }

    /**
     * 答题记录断言类
     */
    public static class AnswerRecordAssert extends AbstractAssert<AnswerRecordAssert, AnswerRecord> {

        public AnswerRecordAssert(AnswerRecord answerRecord) {
            super(answerRecord, AnswerRecordAssert.class);
        }

        public AnswerRecordAssert hasUserAnswer(String expected) {
            isNotNull();
            if (!Objects.equals(actual.getUserAnswer(), expected)) {
                failWithMessage("Expected user answer to be <%s> but was <%s>",
                        expected, actual.getUserAnswer());
            }
            return this;
        }

        public AnswerRecordAssert isCorrect() {
            isNotNull();
            if (!Boolean.TRUE.equals(actual.getCorrect())) {
                failWithMessage("Expected answer record to be correct but was incorrect");
            }
            return this;
        }

        public AnswerRecordAssert isIncorrect() {
            isNotNull();
            if (!Boolean.FALSE.equals(actual.getCorrect())) {
                failWithMessage("Expected answer record to be incorrect but was correct");
            }
            return this;
        }

    }
}