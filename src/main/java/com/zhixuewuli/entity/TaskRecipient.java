package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Durable assignment membership; account deactivation does not remove historical participation. */
@Entity
@Table(name="task_recipients",uniqueConstraints=@UniqueConstraint(name="uk_task_recipient",columnNames={"event_id","user_id"}))
@Data
public class TaskRecipient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id",nullable=false) private Long eventId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(nullable=false,length=20) private String source;
}
