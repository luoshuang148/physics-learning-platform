package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.dto.request.AssistantIntentRequest;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves learning actions without a second model call. Context is task data, never authorization. */
@Service
public class AssistantIntentService {
    private static final Pattern PLAN = Pattern.compile("复习计划|学习计划|复习安排|安排.{0,12}复习|复习.{0,12}(安排|计划)|制定.{0,12}计划");
    private static final Pattern NO_SEARCH = Pattern.compile("(不用|不要|别|无需).{0,8}(搜|找题|检索|题库)");
    private static final Pattern SEARCH_ACTION = Pattern.compile("搜题|找题|搜.{0,20}题|找.{0,20}(题|练习)|[给来推].{0,24}(练习|题)|有没有.{0,20}题|类似.{0,8}题|同类题|相似题");
    private static final Pattern SEARCH = Pattern.compile("题库|平台.{0,20}(题|练习|解析)");
    private static final Pattern WRONG = Pattern.compile("这[道个]题|这题|第[0-9一二三四五六七八九十百]+道?题|我的错题|分析.{0,8}错题|错题.{0,8}(分析|讲|辅导)|为什么.{0,6}(做错|答错|错了)");
    private static final Pattern FOLLOW_UP = Pattern.compile("^(再|继续|换|改成|改为|调整|还有|能不能再|可以再|难一点|简单|容易|难点|排除|不要重复|没做过|只要|讲详细|解释一下|为什么|怎么推导)");
    private static final Pattern NEW_CONCEPT = Pattern.compile(".{2,}(是什么|怎么理解|什么意思|的定义|的原理|如何理解|怎么计算|如何计算)[？?]?$" );
    private static final Pattern EXPLICIT_EXPLANATION = Pattern.compile("^(解释一下|讲解一下|讲讲|解释|讲解).{2,}");
    private static final Pattern DAYS = Pattern.compile("([0-9]{1,3}|[一二两三四五六七八九十])\\s*天");

    public record Resolution(String intent, String query, int days, boolean excludeAttempted) {}

    public Resolution resolve(AssistantIntentRequest request) {
        String question = request.getQuestion().trim();
        String previous = request.getPreviousQuestion();
        String previousIntent = request.getPreviousIntent();
        boolean followUp = previous != null && !previous.isBlank()
                && FOLLOW_UP.matcher(question).find() && !NEW_CONCEPT.matcher(question).matches()
                && !EXPLICIT_EXPLANATION.matcher(question).matches();
        String intent;
        if (NO_SEARCH.matcher(question).find()) intent = "GENERAL";
        else if (PLAN.matcher(question).find()) intent = "PLAN";
        else if (SEARCH_ACTION.matcher(question).find()) intent = "PLATFORM";
        else if (WRONG.matcher(question).find()) intent = "WRONG";
        else if (SEARCH.matcher(question).find()) intent = "PLATFORM";
        else if (followUp && Set.of("GENERAL", "PLATFORM", "WRONG", "PLAN").contains(previousIntent == null ? "" : previousIntent)) intent = previousIntent;
        else intent = "GENERAL";
        String query = followUp && intent.equals(previousIntent) ? previous + "；" + question : question;
        if (query.length() > 1600) query = query.substring(0, 1600);
        var days = DAYS.matcher(question);
        int duration = question.contains("一周") ? 7 : 3;
        if (days.find()) {
            String number = days.group(1);
            duration = number.matches("[0-9]+") ? Integer.parseInt(number)
                    : "一二三四五六七八九十".indexOf(number.replace('两', '二')) + 1;
        }
        boolean exclude = Pattern.compile("(排除|不要|不含|没|未).{0,8}(做过|做的|做题)|没做过|未做过").matcher(question).find();
        return new Resolution(intent, query, Math.max(1, Math.min(7, duration)), exclude);
    }
}
