package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Immutable, ordered task content; deliberately has no foreign key to the mutable question bank. */
@Data
@Entity
@Table(name="teacher_event_questions", uniqueConstraints={
 @UniqueConstraint(name="uk_event_question",columnNames={"event_id","question_id"}),
 @UniqueConstraint(name="uk_event_position",columnNames={"event_id","position_number"})})
public class TeacherEventQuestion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="event_id",insertable=false,updatable=false)
 @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
 private TeacherEvent event;
 @Column(name="bank_question_id") private Long bankQuestionId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="bank_question_id",insertable=false,updatable=false)
 private Question bankQuestion;
 @Column(name="question_id",nullable=false) private Long questionId;
 @Column(name="position_number",nullable=false) private int positionNumber;
 @Column(name="snapshot_json",columnDefinition="LONGTEXT",nullable=false) private String snapshotJson;
 @Column(name="snapshot_origin",nullable=false,length=20) private String snapshotOrigin="PUBLISHED";
}
