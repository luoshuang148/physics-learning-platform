package com.zhixuewuli.service.ai.report;

import com.zhixuewuli.entity.AnswerRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, text-only AI input. Never selects image bodies or full JSON snapshots. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ReportInputRepository {
    public static final int MAX_ROWS=5000, MAX_FIELD_CHARS=4096, MAX_INPUT_BYTES=128*1024;
    private final JdbcTemplate jdbc;

    public List<AnswerRecord> student(Long userId,String sessionId,Long eventId) {
        return eventId==null ? load("a.user_id=? AND a.session_id=?",List.of(userId,sessionId))
                : load("a.user_id=? AND a.event_id=?",List.of(userId,eventId));
    }
    public List<AnswerRecord> teacher(Long eventId) {
        return eventId==null ? load("a.event_id=(SELECT id FROM teacher_events WHERE is_global=true)",List.of())
                : load("a.event_id=?",List.of(eventId));
    }
    public List<AnswerRecord> question(Long questionId) {
        return load("a.question_id=?",List.of(questionId));
    }
    private List<AnswerRecord> load(String scope,List<Object> parameters) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM answer_records a WHERE "+scope,Long.class,parameters.toArray());
        if(count!=null && count>MAX_ROWS)throw tooLarge();
        List<AnswerRecord> result=new ArrayList<>();long cursor=Long.MAX_VALUE;int bytes=4096;
        while(true) {
            List<Object> args=new ArrayList<>(parameters);args.add(cursor);
            var batch=jdbc.query("""
                SELECT a.id,a.user_id,a.event_id,a.question_id,a.session_id,a.selected_option,a.correct,
                 a.practice,a.attempt_order,a.answered_at,a.timestamp_utc,
                 LEFT(a.error_reason,4097) AS reason,
                 LEFT(COALESCE(a.content_snapshot,q.content),4097) AS content,
                 LEFT(CASE WHEN a.content_snapshot IS NULL THEN q.options ELSE a.options_snapshot END,4097) AS options_text,
                 LEFT(CASE WHEN a.content_snapshot IS NULL THEN q.correct_option ELSE a.correct_snapshot END,4097) AS reference_answer,
                 LEFT(CASE WHEN a.content_snapshot IS NULL THEN q.explanation ELSE a.analysis_snapshot END,4097) AS explanation,
                 CASE WHEN a.content_snapshot IS NULL THEN q.difficulty ELSE a.difficulty_snapshot END AS difficulty,
                 (a.content_snapshot IS NULL) AS legacy_fallback,
                 (a.content_snapshot IS NULL AND a.question_snapshot IS NOT NULL) AS requires_repair
                FROM answer_records a LEFT JOIN questions q ON q.id=a.question_id WHERE
                """+scope+" AND a.attempt_order<? ORDER BY a.attempt_order DESC LIMIT 100",(rs,n)->{
                if(rs.getBoolean("requires_repair"))throw new com.zhixuewuli.common.exception.BusinessException("历史快照缺少文本索引，请联系教师核对后再生成报告");
                AnswerRecord row=new AnswerRecord();row.setId(rs.getString("id"));row.setUserId(rs.getLong("user_id"));
                row.setEventId(rs.getObject("event_id",Long.class));row.setQuestionId(rs.getLong("question_id"));
                row.setSessionId(rs.getString("session_id"));row.setUserAnswer(rs.getString("selected_option"));
                row.setCorrect(rs.getObject("correct",Boolean.class));row.setPractice(rs.getBoolean("practice"));
                row.setAttemptOrder(rs.getLong("attempt_order"));row.setAnsweredAt(rs.getTimestamp("answered_at").toLocalDateTime());
                row.setTimestampUtc(rs.getBoolean("timestamp_utc"));row.setErrorReason(bounded(rs.getString("reason")));
                row.setContentSnapshot(bounded(rs.getString("content")));row.setOptionsSnapshot(bounded(rs.getString("options_text")));
                row.setCorrectSnapshot(bounded(rs.getString("reference_answer")));row.setAnalysisSnapshot(bounded(rs.getString("explanation")));
                row.setDifficultySnapshot(rs.getObject("difficulty",Integer.class));row.setReportLegacyBankFallback(rs.getBoolean("legacy_fallback"));
                return row;
            },args.toArray());
            for(var row:batch) {
                bytes+=512+size(row.getContentSnapshot())+size(row.getOptionsSnapshot())+size(row.getAnalysisSnapshot())+size(row.getErrorReason());
                if(bytes>MAX_INPUT_BYTES || result.size()>=MAX_ROWS)throw tooLarge();
                result.add(row);cursor=row.getAttemptOrder();
            }
            if(batch.size()<100)return List.copyOf(result);
        }
    }
    private static int size(String value){return value==null?0:value.getBytes(StandardCharsets.UTF_8).length;}
    private static String bounded(String value){if(value!=null&&value.length()>MAX_FIELD_CHARS)throw tooLarge();return value;}
    private static com.zhixuewuli.common.exception.BusinessException tooLarge(){return new com.zhixuewuli.common.exception.BusinessException("报告输入超过上限，请缩小任务范围或精简题目内容");}
}
