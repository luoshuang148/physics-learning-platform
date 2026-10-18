package com.zhixuewuli.common.util;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhixuewuli.dto.response.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class LearningTimestampContractTest {
 private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 @Test void newUtcAnswersAndTasksHaveAnOffsetOnTheWire() throws Exception {
  var time=LocalDateTime.of(2026,10,18,2,0);
  var answer=AnswerRecordResponse.builder().answeredAt(time).requestId("new-key").build();
  var task=TeacherEventResponse.builder().eventTimestamp(time).build();
  assertThat(json.readTree(json.writeValueAsString(answer)).get("answeredAt").asText()).isEqualTo("2026-10-18T02:00:00Z");
  assertThat(json.readTree(json.writeValueAsString(task)).get("eventTimestamp").asText()).isEqualTo("2026-10-18T02:00:00Z");
  assertThat(Instant.parse("2026-10-18T02:00:00Z").atZone(ZoneId.of("Asia/Shanghai")).getHour()).isEqualTo(10);
 }
 @Test void legacyLocalClockValuesRemainUnshiftedAndExplicit() throws Exception {
  var answer=AnswerRecordResponse.builder().answeredAt(LocalDateTime.of(2026,10,18,2,0)).build();
  ReflectionTestUtils.setField(answer,"timestampUtc",false);
  var tree=json.readTree(json.writeValueAsString(answer));
  assertThat(tree.get("answeredAt").asText()).isEqualTo("2026-10-18T02:00:00");
  assertThat(tree.get("timeBasis").asText()).isEqualTo("LEGACY_LOCAL");
 }
}
