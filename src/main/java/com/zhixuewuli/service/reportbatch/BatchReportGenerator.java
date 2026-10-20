package com.zhixuewuli.service.reportbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.ai.report.ReportInputRepository;
import com.zhixuewuli.service.ai.report.ReportPreparation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 后台批量报告生成器：复用学生报告的输入准备、统计口径与内容净化，
 * 但不经过 SSE。模型调用在数据库事务之外执行，落库由调用方在短事务中完成。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchReportGenerator {

    private final ReportInputRepository reportInputs;
    private final AliyunAIClient aliyunAIClient;
    private final StudentReportRepository studentReports;
    private final UserRepository users;
    private final AIBusinessProperties aiBusinessProperties;
    private final ReportBatchProperties reportBatchProperties;
    private final ObjectMapper objectMapper;

    public record Generated(String content, String evidenceJson, String digest, String revision) {}

    /** 只读输入 + 模型调用，必须在事务外执行。 */
    public Generated generate(Long userId, Long eventId) {
        if (!aiBusinessProperties.isEnabled()) {
            throw new ReportBatchAiDisabledException("AI 能力未启用，无法批量生成报告。");
        }
        if (aiBusinessProperties.getStudentReportGenerationModel() == null
                || aiBusinessProperties.getStudentReportGenerationModel().isBlank()) {
            throw new ReportBatchAiDisabledException("未配置学生报告模型，无法批量生成报告。");
        }
        List<AnswerRecord> records = reportInputs.student(userId, null, eventId);
        if (records.isEmpty()) {
            throw new BusinessException("该任务下无作答记录，无法生成报告");
        }
        String model = aiBusinessProperties.getStudentReportGenerationModel();
        ReportPreparation.Prepared prepared =
                ReportPreparation.prepare(records, Map.of(), objectMapper, false, model);
        String streamed = aliyunAIClient.generateBlocking(prepared.prompt(), model,
                reportBatchProperties.getModelTimeout());
        String content = prepared.header() + streamed;
        return new Generated(
                content,
                objectMapper.valueToTree(prepared.evidence()).toString(),
                String.valueOf(prepared.evidence().get("inputSha256")),
                String.valueOf(prepared.evidence().get("answerRevision")));
    }

    /** 在调用方的事务内保存不可变的学生报告，返回报告 ID。 */
    public Long persist(Long userId, Long eventId, Long batchId, Generated generated) {
        User student = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("学生用户不存在，无法保存报告"));
        StudentReport report = new StudentReport();
        report.setUserId(userId);
        report.setRealName(student.getRealName());
        report.setSessionId("batch:" + batchId);
        report.setReportContent(generated.content());
        report.setEventId(eventId);
        report.setEvidenceJson(generated.evidenceJson());
        return studentReports.save(report).getId();
    }
}
