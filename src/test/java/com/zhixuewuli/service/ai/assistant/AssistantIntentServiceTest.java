package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.dto.request.AssistantIntentRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AssistantIntentServiceTest {
    private final AssistantIntentService service = new AssistantIntentService();

    private AssistantIntentRequest request(String question, String previousQuestion, String previousIntent) {
        var request = new AssistantIntentRequest();
        request.setQuestion(question);
        request.setPreviousQuestion(previousQuestion);
        request.setPreviousIntent(previousIntent);
        return request;
    }

    @Test void routesNaturalRequestsToTheAppropriateCapability() {
        assertEquals("GENERAL", service.resolve(request("动量守恒怎么理解？", null, null)).intent());
        assertEquals("PLATFORM", service.resolve(request("平台有没有动量守恒的题？", null, null)).intent());
        assertEquals("PLATFORM", service.resolve(request("给我找几道牛顿第二定律练习题", null, null)).intent());
        assertEquals("WRONG", service.resolve(request("这道题为什么错了？", null, null)).intent());
        assertEquals("PLAN", service.resolve(request("根据这次作业安排三天复习", null, null)).intent());
    }

    @Test void followUpRetrievalKeepsItsSubjectAndFilters() {
        var result = service.resolve(request("再来两道简单的，排除我做过的", "帮我找动量守恒练习题", "PLATFORM"));
        assertEquals("PLATFORM", result.intent());
        assertTrue(result.query().contains("动量守恒"));
        assertTrue(result.query().contains("简单"));
        assertTrue(result.excludeAttempted());
    }

    @Test void changingTopicDoesNotStayInRetrievalMode() {
        var result = service.resolve(request("那电场强度是什么？", "找动量守恒练习题", "PLATFORM"));
        assertEquals("GENERAL", service.resolve(request("解释一下电场强度", "找动量守恒练习题", "PLATFORM")).intent());
        assertEquals("GENERAL", result.intent());
        assertEquals("那电场强度是什么？", result.query());
    }

    @Test void explicitSimilarQuestionSearchWinsOverTheReferenceToThisQuestion() {
        assertEquals("PLATFORM", service.resolve(request("帮我找类似这道题的练习", null, null)).intent());
        assertEquals("PLATFORM", service.resolve(request("给我几道动量守恒的题", null, null)).intent());
        assertEquals("PLAN", service.resolve(request("改为五天", "安排三天复习", "PLAN")).intent());
    }

    @Test void respectsAnExplicitRequestNotToSearch() {
        assertEquals("GENERAL", service.resolve(request("不用搜平台题库，直接解释动量守恒", null, null)).intent());
        assertEquals("GENERAL", service.resolve(request("不要找题了，讲讲牛顿定律", "找牛顿定律练习题", "PLATFORM")).intent());
    }

    @Test void extractsChineseAndArabicPlanDurations() {
        assertEquals(3, service.resolve(request("安排三天复习", null, null)).days());
        assertEquals(5, service.resolve(request("给我一个5天的复习计划", null, null)).days());
        assertEquals(7, service.resolve(request("帮我安排一周复习", null, null)).days());
        assertEquals(7, service.resolve(request("安排30天复习", null, null)).days());
    }

    @Test void questionContextDoesNotTurnConceptualErrorsIntoWrongAnswerAnalysis() {
        assertEquals("GENERAL", service.resolve(request("为什么这个说法是错的：力越大速度越大？", null, null)).intent());
        assertEquals("WRONG", service.resolve(request("解释一下这道题", null, null)).intent());
        assertEquals("GENERAL", service.resolve(request("继续解释", "动量守恒怎么理解", "GENERAL")).intent());
    }
}
