package com.zhixuewuli.service.ai.unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.ai.report.ReportPreparation;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class ReportEvaluationFixturesTest {
 @Test void fixedSyntheticCorpusHasValidReferencesAndDeterministicEvidence() throws Exception {
  var mapper=new ObjectMapper();
  List<String> lines;
  try(var in=Objects.requireNonNull(ReportEvaluationFixturesTest.class.getResourceAsStream("/ai-evaluation/cases.jsonl"),"missing classpath ai-evaluation/cases.jsonl");
      var reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
   lines=reader.lines().toList();
  }
  assertThat(lines).hasSize(10);
  Set<String> ids=new HashSet<>();
  for(String line:lines){
   var c=mapper.readTree(line);assertThat(ids.add(c.get("id").asText())).isTrue();assertThat(c.get("reviewStatus").asText()).isEqualTo("pending");assertThat(c.get("rubric").size()).isGreaterThanOrEqualTo(3);
   assertThat(c.get("options").has(c.get("referenceAnswer").asText())).isTrue();
   Question q=new Question();q.setContent(c.get("question").asText());q.setOptions(c.get("options").toString());q.setCorrectOption(c.get("referenceAnswer").asText());
   var r=new AnswerRecord();r.setUserId(987654L);r.setQuestionId(1L);r.setEventId(1L);r.setQuestionSnapshot(mapper.writeValueAsString(q));r.setUserAnswer(c.get("studentAnswer").asText());r.setCorrect(false);
   var one=ReportPreparation.prepare(List.of(r),Map.of(),mapper,true,"mock-no-network");var two=ReportPreparation.prepare(List.of(r),Map.of(),mapper,true,"mock-no-network");
   assertThat(one.evidence().get("inputSha256")).isEqualTo(two.evidence().get("inputSha256"));assertThat(one.evidence().get("assignmentCorrect")).isEqualTo(0L);assertThat(one.evidence().get("assignments")).isEqualTo(1L);assertThat(one.prompt()).doesNotContain("987654").contains(q.getContent());
  }
 }
}
