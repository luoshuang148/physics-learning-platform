package com.zhixuewuli.service.ai.unit;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.ai.impl.AliyunAIReportServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("unit")
class ReportGenerationConcurrencyTest {
 @InjectMocks AliyunAIReportServiceImpl service;
 @Mock UserRepository users;@Mock StudentReportRepository reports;
 @BeforeEach void setup(){MockitoAnnotations.openMocks(this);}
 @Test void concurrentAndExistingDuplicateScopesCanRegenerateWithoutLosingGenerations() throws Exception {
  var user=new User();user.setRealName("local-only");when(users.findById(1L)).thenReturn(Optional.of(user));
  List<StudentReport> saved=new CopyOnWriteArrayList<>();var firstLookups=new CyclicBarrier(2);var queries=new java.util.concurrent.atomic.AtomicInteger();
  lenient().when(reports.findByUserIdAndSessionIdAndEventId(eq(1L),eq("same"),isNull())).thenAnswer(i->{
   if(queries.incrementAndGet()<=2){firstLookups.await(5,TimeUnit.SECONDS);return Optional.empty();}
   if(saved.size()>1)throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1,saved.size());
   return saved.stream().findFirst();
  });
  when(reports.save(any())).thenAnswer(i->{StudentReport row=i.getArgument(0);saved.add(row);return row;});
  try(var pool=Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->ReflectionTestUtils.invokeMethod(service,"saveStudentReport",1L,"same",null,"first","{}"));
   var b=pool.submit(()->ReflectionTestUtils.invokeMethod(service,"saveStudentReport",1L,"same",null,"second","{}"));
   a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
  }
  assertThatCode(()->ReflectionTestUtils.invokeMethod(service,"saveStudentReport",1L,"same",null,"third","{}")).doesNotThrowAnyException();
  assertThat(saved).hasSize(3);assertThat(saved.stream().map(StudentReport::getReportContent)).containsExactlyInAnyOrder("first","second","third");
 }
}
