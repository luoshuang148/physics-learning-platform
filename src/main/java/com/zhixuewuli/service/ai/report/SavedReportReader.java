package com.zhixuewuli.service.ai.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.response.StudentTaskResultResponse.SavedReport;
import com.zhixuewuli.repository.StudentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavedReportReader {
    private final StudentReportRepository reports;
    private final ObjectMapper json;

    public SavedReport matching(Long userId,Long eventId,String revision) {
        if ("0".equals(revision)) return null;
        return reports.findFirstByUserIdAndEventIdOrderByUpdatedAtDescIdDesc(userId,eventId).map(report -> {
            try {
                var evidence=json.readTree(report.getEvidenceJson());
                if (!revision.equals(evidence.path("answerRevision").asText())) return null;
                return new SavedReport(report.getReportContent(),revision,evidence.path("generatedAt").asText(""));
            } catch (Exception invalidLegacyEvidence) { return null; }
        }).orElse(null);
    }
}
