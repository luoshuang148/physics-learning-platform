package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.entity.QuestionIndexJob;
import com.zhixuewuli.repository.QuestionIndexJobRepository;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 题目索引任务入队。业务修改与索引任务同事务提交（调用方负责事务边界）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionSourceIndexService {

    public static final String JOB_INDEX = "INDEX";
    public static final String JOB_DELETE = "DELETE";

    private final QuestionIndexJobRepository jobs;
    private final QuestionSearchUnitRepository searchUnits;

    /**
     * 为题目当前修订排队（或重排）索引任务。幂等：同 (questionId, revision, INDEX) 只保留一行。
     */
    public QuestionIndexJob enqueueCurrent(long questionId, long revision) {
        var existing = jobs.findByQuestionIdAndRevisionAndJobType(questionId, revision, JOB_INDEX);
        QuestionIndexJob job = existing.orElseGet(() -> {
            var fresh = new QuestionIndexJob();
            fresh.setQuestionId(questionId);
            fresh.setRevision(revision);
            fresh.setJobType(JOB_INDEX);
            return fresh;
        });
        if (job.getStatus() == QuestionIndexJob.Status.READY
                || job.getStatus() == QuestionIndexJob.Status.INDEXING) {
            // 已就绪/进行中则不重复排队；失败/过期由 worker 重试
            return job;
        }
        job.setStatus(QuestionIndexJob.Status.PENDING);
        job.setAttempts(0);
        job.setFailureCode(null);
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        return jobs.save(job);
    }

    /**
     * 排队删除（禁用/删除题目）。保留墓碑，向量异步清理。
     */
    public QuestionIndexJob enqueueDelete(long questionId, long revision) {
        var existing = jobs.findByQuestionIdAndRevisionAndJobType(questionId, revision, JOB_DELETE);
        QuestionIndexJob job = existing.orElseGet(() -> {
            var fresh = new QuestionIndexJob();
            fresh.setQuestionId(questionId);
            fresh.setRevision(revision);
            fresh.setJobType(JOB_DELETE);
            return fresh;
        });
        job.setStatus(QuestionIndexJob.Status.PENDING);
        job.setAttempts(0);
        job.setFailureCode(null);
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        return jobs.save(job);
    }

    /**
     * 将检索单元写入 MySQL（同事务）。revision 变化时清理旧单元。
     */
    public void replaceSearchUnits(long questionId, long revision,
                                   java.util.List<com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchUnit> units) {
        searchUnits.deleteByQuestionIdAndRevisionLessThan(questionId, revision);
        var entities = units.stream().map(u -> {
            var e = new com.zhixuewuli.entity.QuestionSearchUnit();
            e.setQuestionId(u.questionId());
            e.setRevision(u.revision());
            e.setPart(u.part().name());
            e.setOrdinalNo(u.ordinal());
            e.setUnitText(u.text());
            e.setContentHash(u.contentHash());
            e.setKnowledgePointCodes(toJson(u.knowledgePointCodes()));
            e.setDifficulty(u.difficulty());
            return e;
        }).toList();
        // 先删当前 revision 旧行再插，保证一题一修订幂等
        searchUnits.findUnits(questionId, revision).forEach(searchUnits::delete);
        searchUnits.saveAll(entities);
    }

    private String toJson(java.util.List<String> codes) {
        if (codes == null || codes.isEmpty()) return "[]";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(codes);
        } catch (Exception e) {
            return "[]";
        }
    }
}
