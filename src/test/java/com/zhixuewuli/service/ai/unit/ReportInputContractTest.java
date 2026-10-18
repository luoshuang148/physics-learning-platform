package com.zhixuewuli.service.ai.unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.ai.impl.AliyunAIReportServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class ReportInputContractTest {
 @InjectMocks AliyunAIReportServiceImpl service;
 @InjectMocks com.zhixuewuli.service.questionErrorAnalysis.impl.QuestionErrorAnalysisServiceImpl questionService;
 @Mock com.zhixuewuli.service.ai.report.ReportInputRepository inputs;
 @Mock com.zhixuewuli.repository.QuestionRepository questions;
 @Mock com.zhixuewuli.repository.AnswerRecordRepository answers;
 @Spy com.zhixuewuli.common.config.properties.AIBusinessProperties properties=new com.zhixuewuli.common.config.properties.AIBusinessProperties();
 @Spy ObjectMapper mapper=new ObjectMapper();
 @BeforeEach void setup(){MockitoAnnotations.openMocks(this);}
 private AnswerRecord record(long event,String text,boolean practice,boolean correct) throws Exception {
  Question q=new Question();q.setId(9L);q.setContent(text);q.setOptions("{\"A\":\"one\",\"B\":\"two\"}");q.setCorrectOption("A");
  var r=new AnswerRecord();r.setId(String.valueOf(event));r.setUserId(123456L);r.setEventId(event);r.setQuestionId(9L);r.setPractice(practice);r.setCorrect(correct);r.setUserAnswer("B");r.setAnsweredAt(LocalDateTime.of(2026,1,1,0,0));r.setQuestionSnapshot(mapper.writeValueAsString(q));return r;
 }
 @Test void studentPromptOmitsNameAndUsesEachHistoricalTaskVersion() throws Exception {
  var method=AliyunAIReportServiceImpl.class.getDeclaredMethod("buildStudentReportPrompt",List.class,Map.class,String.class);method.setAccessible(true);
  String prompt=(String)method.invoke(service,List.of(record(1,"OLD_TASK",false,false),record(2,"NEW_TASK",true,true)),Map.of(),"真实姓名张三");
  assertThat(prompt).doesNotContain("真实姓名张三","123456").contains("OLD_TASK","NEW_TASK","原始作答","补练");
 }
 @Test void teacherPromptUsesAnonymousLabelsAndExactSeparatedCounts() throws Exception {
  var method=AliyunAIReportServiceImpl.class.getDeclaredMethod("buildTeacherAnalysisPrompt",List.class,Map.class,Map.class);method.setAccessible(true);
  String prompt=(String)method.invoke(service,List.of(record(1,"OLD_TASK",false,false),record(2,"NEW_TASK",true,true)),Map.of(),Map.of(123456L,"真实姓名张三"));
  assertThat(prompt).doesNotContain("真实姓名张三","123456").contains("原始作答：1","原始正确：0","补练：1","补练正确：1","学生1");
 }
 @Test void teacherQuestionPromptAlsoOmitsPersonalNamesAndUsesSnapshot() throws Exception {
  var r=record(1,"ORIGINAL_TEACHER_SNAPSHOT",false,false);var user=new User();user.setRealName("PRIVATE_PERSON_NAME");r.setUser(user);
  var bank=new Question();bank.setContent("CHANGED_BANK");bank.setId(9L);
  Mockito.lenient().when(questions.findById(9L)).thenReturn(Optional.of(bank));Mockito.when(inputs.question(9L)).thenReturn(List.of(r));
  var method=questionService.getClass().getDeclaredMethod("buildTeacherQuestionAnalysisContext",Long.class);method.setAccessible(true);
  assertThat((String)method.invoke(questionService,9L)).contains("ORIGINAL_TEACHER_SNAPSHOT").doesNotContain("PRIVATE_PERSON_NAME","CHANGED_BANK");
 }

}
