package com.zhixuewuli.service.teacherEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.common.util.OptionMappingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class TaskQuestionService {
 private final TeacherEventQuestionRepository snapshots;
 private final QuestionRepository questions;
 private final ObjectMapper json;
 private final OptionMappingUtil options;
 public List<Question> prepare(TeacherEventCreateRequest request) {
  return prepare(request, false);
 }
 public List<Question> prepare(TeacherEventCreateRequest request, boolean allowEmpty) {
  List<Long> ids=request.getQuestionIds();
  if(ids==null) {
   String legacy=request.getEventDescription();
   if(legacy==null || !legacy.startsWith("QIDS:")) ids=List.of();
   else try { ids=Arrays.stream(legacy.substring(5).split(",")).map(String::trim).map(Long::valueOf).toList(); }
   catch(RuntimeException e){throw new IllegalArgumentException("旧任务题目列表无效");}
  }
  if((!allowEmpty && ids.isEmpty())||ids.size()>200||new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException(allowEmpty ? "任务最多包含200道不同题目" : "任务须包含1到200道不同题目");
  List<Question> result=new ArrayList<>();
  for(Long id:ids) result.add(questions.findById(id).orElseThrow(()->new IllegalArgumentException("任务题目不存在")));
  return result;
 }
 public void replace(Long eventId,List<Question> ordered) {
  snapshots.deleteByEventId(eventId);
  capture(eventId, ordered);
 }
 public void capture(Long eventId,List<Question> ordered) {
  int position=0;
  for(Question question:ordered) {
   var row=new TeacherEventQuestion();row.setEventId(eventId);row.setQuestionId(question.getId());row.setBankQuestionId(question.getId());row.setPositionNumber(position++);
   try {row.setSnapshotJson(json.writeValueAsString(question));}catch(Exception e){throw new IllegalStateException("题目快照保存失败",e);}
   snapshots.save(row);
  }
 }
 public List<Long> ids(Long eventId) {return snapshots.findQuestionIdsByEventId(eventId);}
 public long count(Long eventId) {return snapshots.countByEventId(eventId);}
 public java.util.List<com.zhixuewuli.repository.TeacherEventQuestionRepository.TaskQuestionCountRow> countByEventIds(java.util.Collection<Long> eventIds) {return snapshots.countByEventIds(eventIds);}
 public Question get(Long eventId,Long questionId) {
  var row=snapshots.findByEventIdAndQuestionId(eventId,questionId).orElseThrow(()->new IllegalArgumentException("题目不属于该任务"));
  if("MISSING".equals(row.getSnapshotOrigin())) throw new IllegalArgumentException("升级前题目已缺失，无法恢复原题");
  return decode(row);
 }
 private Question decode(TeacherEventQuestion row){try{return json.readValue(row.getSnapshotJson(),Question.class);}catch(Exception e){throw new IllegalStateException("任务快照损坏",e);}}
 public List<QuestionResponse> responses(Long eventId){return snapshots.findByEventIdOrderByPositionNumberAsc(eventId).stream().map(row->{
  Question q=decode(row);
  return QuestionResponse.builder().id(row.getQuestionId()).content(q.getContent()).options("MISSING".equals(row.getSnapshotOrigin()) ? java.util.Map.of() : options.jsonToOptions(q.getOptions()))
   .correctOption(q.getCorrectOption()).explanation(q.getExplanation()).difficulty(q.getDifficulty()).imageBase64(q.getImageBase64())
   .knowledgePointCodes(q.getKnowledgePointCodes())
   .snapshotOrigin(row.getSnapshotOrigin()).build();
 }).toList();}
}
