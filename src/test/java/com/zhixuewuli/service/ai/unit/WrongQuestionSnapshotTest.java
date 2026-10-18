package com.zhixuewuli.service.ai.unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.ai.impl.ZhiPuAIAssistantServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class WrongQuestionSnapshotTest {
 @InjectMocks ZhiPuAIAssistantServiceImpl service;
 @Mock AnswerRecordRepository answers;@Mock QuestionRepository questions;
 @Spy ObjectMapper mapper=new ObjectMapper();@Spy AIBusinessProperties properties=new AIBusinessProperties();
 @BeforeEach void setup(){MockitoAnnotations.openMocks(this);}
 @Test void contextUsesRecordedVersionAfterQuestionBankEdits() throws Exception {
  var original=new Question();original.setContent("ORIGINAL_IMMUTABLE");original.setCorrectOption("B");
  var changed=new Question();changed.setContent("EDITED_BANK");changed.setCorrectOption("A");
  var record=new AnswerRecord();record.setUserId(1L);record.setQuestionId(9L);record.setUserAnswer("A");record.setQuestionSnapshot(mapper.writeValueAsString(original));
  when(answers.findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(1L,9L)).thenReturn(Optional.of(record));lenient().when(questions.findById(9L)).thenReturn(Optional.of(changed));
  var method=ZhiPuAIAssistantServiceImpl.class.getDeclaredMethod("buildWrongQuestionContext",Long.class,Long.class,String.class);method.setAccessible(true);
  assertThat((String)method.invoke(service,1L,9L,"session")).contains("ORIGINAL_IMMUTABLE","参考答案：选项 B").doesNotContain("EDITED_BANK");
 }
}
